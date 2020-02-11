package be.cytomine.social

import be.cytomine.project.Project
import be.cytomine.security.SecUser
import be.cytomine.security.User
import be.cytomine.sql.AnnotationListing
import be.cytomine.sql.UserAnnotationListing
import be.cytomine.utils.JSONUtils
import be.cytomine.utils.ModelService
import grails.transaction.Transactional
import groovy.time.TimeCategory
import org.hibernate.criterion.Projections
import org.springframework.web.context.request.RequestContextHolder

import static org.springframework.security.acls.domain.BasePermission.READ
import static org.springframework.security.acls.domain.BasePermission.WRITE

@Transactional
class ProjectConnectionService extends ModelService {

    def securityACLService
    def dataSource
    def mongo
    def noSQLCollectionService
    def imageConsultationService
    def secUserService

    def add(def json){

        SecUser user = cytomineService.getCurrentUser()
        Project project = Project.read(JSONUtils.getJSONAttrLong(json,"project",0))
        securityACLService.check(project,READ)
        closeLastProjectConnection(user.id, project.id, new Date())
        PersistentProjectConnection connection = new PersistentProjectConnection()
        connection.user = user.id
        connection.project = project.id
        connection.created = new Date()
        connection.session = RequestContextHolder.currentRequestAttributes().getSessionId()
        connection.os = json.os
        connection.browser = json.browser
        connection.browserVersion = json.browserVersion
        connection.insert(flush:true, failOnError : true) //don't use save (stateless collection)
        return connection
    }

    def lastConnectionInProject(Project project, def searchParameters = [], String sortProperty = "created", String sortDirection = "desc", Long max = 0, Long offset = 0){
        lastConnectionInProject(project, null, searchParameters, sortProperty, sortDirection, max, offset)
    }
    def lastConnectionInProject(Project project, Long userId, def searchParameters = [], String sortProperty = "created", String sortDirection = "desc", Long max = 0, Long offset = 0){
        if(userId) {
            SecUser user = secUserService.read(userId)
            securityACLService.checkIsSameUserOrAdminContainer(project, user , cytomineService.currentUser)
        } else {
            securityACLService.check(project,WRITE)
        }

        if (userId) {
            return PersistentProjectConnection.findAllByUserAndProject(userId, project.id, [sort: sortProperty, order: sortDirection, max: 1])
        }

        def results = []
        def db = mongo.getDB(noSQLCollectionService.getDatabaseName())

        def match = [project : project.id]
        def sp = searchParameters.find{it.operator.equals("in") && it.property.equals("user")}
        if(sp) match << [user : [$in :sp.value]]

        def aggregation = [
                [$match:match],
                [$sort : ["$sortProperty": sortDirection.equals("desc") ? -1 : 1]],
                [$group : [_id : '$user', created : [$max :'$created']]],
                [$skip : offset]
        ]
        if(max > 0) aggregation.push([$limit : max])

        def connection = db.persistentProjectConnection.aggregate(aggregation)

        connection.results().each {
            results << [user: it["_id"], created : it["created"]]
        }
        return results
    }

    /**
     * return the last connection in a project by user. If a user (in the userIds array) doesn't have a connection yet, null values will be associated to the user id.
     */
    // Improve : Can be improved if we can do this in mongo directly
    def lastConnectionOfGivenUsersInProject(Project project, def userIds, String sortProperty = "created", String sortDirection = "desc", Long max = 0, Long offset = 0){
        def results = []

        def connected = PersistentProjectConnection.createCriteria().list(sort: "user", order: sortDirection) {
            eq("project", project)
            projections {
                Projections.groupProperty("user")
                property("user")
            }
        }

        def unconnected = userIds - connected
        unconnected = unconnected.collect{[user: it , created : null]}

        if(max == 0) max = unconnected.size() + connected.size() - offset

        if(sortDirection.equals("desc")){
            //if o+l <= #connected ==> return connected with o et l
            // if o+l > #c c then return connected with o et l and append enough "nulls"

            if(offset < connected.size()){
                results = lastConnectionInProject(project, [], sortProperty, sortDirection, max, offset)
            }
            int maxOfUnconnected = Math.max(max - results.size(),0)
            int offsetOfUnconnected = Math.max(offset - connected.size(),0)
            if (maxOfUnconnected > 0 ) results.addAll(unconnected.subList(offsetOfUnconnected,offsetOfUnconnected+maxOfUnconnected))
        } else {
            if(offset + max <= unconnected.size()){
                results = unconnected.subList((int)offset,(int)(offset+max))
            }
            else if(offset + max > unconnected.size() && offset <= unconnected.size()) {
                results = unconnected.subList((int)offset,unconnected.size())
                results.addAll(lastConnectionInProject(project, [], sortProperty, sortDirection, max-(unconnected.size()-offset), 0))
            } else {
                results.addAll(lastConnectionInProject(project, [], sortProperty, sortDirection, max, offset - unconnected.size()))
            }
        }
        return results
    }

    private void closeLastProjectConnection(Long user, Long project, Date before){
        PersistentProjectConnection connection = PersistentProjectConnection.findByUserAndProjectAndCreatedLessThan(user, project, before, [sort: 'created', order: 'desc', max: 1])

        //first connection
        if(connection == null) return;

        //last connection already closed
        if(connection.time) return;

        fillProjectConnection(connection, before)

        connection.save(flush : true)
    }

    def annotationListingService
    private void fillProjectConnection(PersistentProjectConnection connection, Date before = new Date()){
        Date after = connection.created;

        // collect {it.created.getTime} is really slow. I just want the getTime of PersistentConnection
        def db = mongo.getDB(noSQLCollectionService.getDatabaseName())
        def connections = db.persistentConnection.aggregate(
                [$match: [project: connection.project, user: connection.user, $and : [[created: [$gte: after]],[created: [$lte: before]]]]],
                [$sort: [created: 1]],
                [$project: [dateInMillis: [$subtract: ['$created', new Date(0L)]]]]
        );

        def continuousConnections = connections.results().collect { it.dateInMillis }

        //we calculated the gaps between connections to identify the period of non activity
        def continuousConnectionIntervals = []

        continuousConnections.inject(connection.created.time) { result, i ->
            continuousConnectionIntervals << (i-result)
            i
        }

        connection.time = continuousConnectionIntervals.split{it < 30000}[0].sum()
        if(connection.time == null) connection.time=0;

        // count viewed images
        connection.countViewedImages = imageConsultationService.getImagesOfUsersByProjectBetween(connection.user,
                connection.project,after, before).unique({it.image}).size()

        AnnotationListing al = new UserAnnotationListing()
        al.project = connection.project
        al.user = connection.user
        al.beforeThan = before
        al.afterThan = after

        // count created annotations
        connection.countCreatedAnnotations = annotationListingService.listGeneric(al).size()
    }

    def getConnectionByUserAndProject(User user, Project project, Integer limit, Integer offset){
        securityACLService.check(project,WRITE)

        def connections = PersistentProjectConnection.createCriteria().list(sort: "created", order: "desc") {
            eq("user", user)
            eq("project", project)
            firstResult(offset)
            maxResults(limit)
        }

        if(connections.size() == 0) return connections;

        if(!connections[0].time) {
            connections[0] = ((PersistentProjectConnection) connections[0]).clone()
            boolean online = LastConnection.findByProjectAndUser(project, user) != null
            fillProjectConnection(connections[0])
            if(online) {
                connections[0].online = true
            }
        }

        return connections
    }

    def numberOfConnectionsByProjectAndUser(Project project, def searchParameters = [], String sortProperty = "created", String sortDirection = "desc", Long max = 0, Long offset = 0){
        numberOfConnectionsByProjectAndUser(project, null, searchParameters, sortProperty , sortDirection , max, offset)
    }
    def numberOfConnectionsByProjectAndUser(Project project, User user, def searchParameters = [], String sortProperty = "created", String sortDirection = "desc", Long max = 0, Long offset = 0){

        securityACLService.check(project,WRITE)
        def result;
        if(user) {
            def mResult = PersistentProjectConnection.createCriteria().get/*(sort: "created", order: "desc")*/ {
                eq("user", user)
                eq("project", project)
                projections {
                    rowCount()
                }
            }
            result = [[user : user.id,frequency: (int) mResult]]
        } else{
            // what we want
            // db.persistentProjectConnection.aggregate([{$match: {project : ID_PROJECT}}, { $group : { _id : {user:"$user"} , number : { $sum : 1 }}}])

            def db = mongo.getDB(noSQLCollectionService.getDatabaseName())

            def match = [project : project.id]
            def sp = searchParameters.find{it.operator.equals("in") && it.property.equals("user")}
            if(sp) match << [user : [$in :sp.value]]

            def aggregation = [
                    [$match : match],
                    [$group : [_id : [ user: '$user'], "frequency":[$sum:1]]],
                    [$sort : ["$sortProperty": sortDirection.equals("desc") ? -1 : 1]],
                    [$skip : offset]
            ]
            if(max > 0) aggregation.push([$limit : max])

            result = db.persistentProjectConnection.aggregate(aggregation)

            def usersConnections = []
            result.results().each {
                def userId = it["_id"]["user"]
                def frequency = it["frequency"]
                usersConnections << [user: userId, frequency: frequency]
            }
            result = usersConnections
        }
        return result
    }

    /**
     * return the number of project connections by user in a Project. If a user (in the userIds array) doesn't have a connection yet, null values will be associated to the user id.
     */
    // Improve : Can be improved if we can do this in mongo directly
    def numberOfConnectionsOfGivenByProject(Project project, def userIds, String sortProperty = "created", String sortDirection = "desc", Long max = 0, Long offset = 0){
        def results = []

        def connected = PersistentProjectConnection.createCriteria().list(sort: "user", order: sortDirection) {
            eq("project", project)
            projections {
                Projections.groupProperty("user")
                property("user")
            }
        }

        def unconnected = userIds - connected
        unconnected = unconnected.collect{[user: it , frequency : null]}

        if(max == 0) max = unconnected.size() + connected.size() - offset

        if(sortDirection.equals("desc")){
            //if o+l <= #connected ==> return connected with o et l
            // if o+l > #c c then return connected with o et l and append enough "nulls"

            if(offset < connected.size()){
                results = numberOfConnectionsByProjectAndUser(project, [], sortProperty, sortDirection, max, offset)
            }
            int maxOfUnconnected = Math.max(max - results.size(),0)
            int offsetOfUnconnected = Math.max(offset - connected.size(),0)
            if (maxOfUnconnected > 0 ) results.addAll(unconnected.subList(offsetOfUnconnected,offsetOfUnconnected+maxOfUnconnected))
        } else {
            if(offset + max <= unconnected.size()){
                results = unconnected.subList((int)offset,(int)(offset+max))
            }
            else if(offset + max > unconnected.size() && offset <= unconnected.size()) {
                results = unconnected.subList((int)offset,unconnected.size())
                results.addAll(numberOfConnectionsByProjectAndUser(project, [], sortProperty, sortDirection, max-(unconnected.size()-offset), 0))
            } else {
                results.addAll(numberOfConnectionsByProjectAndUser(project, [], sortProperty, sortDirection, max, offset - unconnected.size()))
            }
        }
        return results
    }

    def totalNumberOfConnectionsByProject(){

        securityACLService.checkAdmin(cytomineService.getCurrentUser())
        def result;
        // what we want
        // db.persistentProjectConnection.aggregate([{ $group : { _id : {project:"$project"} , total : { $sum : 1 }}}])

        def db = mongo.getDB(noSQLCollectionService.getDatabaseName())

        result = db.persistentProjectConnection.aggregate(
                [$group : [_id : '$project', "total":[$sum:1]]]
        )

        def projectConnections = []
        result.results().each {
            def projectId = it["_id"]
            def total = it["total"]
            projectConnections << [project: projectId, total: total]
        }
        result = projectConnections
        return result
    }


    def numberOfConnectionsByProjectOrderedByHourAndDays(Project project, Long afterThan = null, User user = null){

        securityACLService.check(project,WRITE)
        // what we want
        //db.persistentProjectConnection.aggregate( {"$match": {$and: [{project : ID_PROJECT}, {created : {$gte : new Date(AFTER) }}]}}, { "$project": { "created": {  "$subtract" : [  "$created",  {  "$add" : [  {"$millisecond" : "$created"}, { "$multiply" : [ {"$second" : "$created"}, 1000 ] }, { "$multiply" : [ {"$minute" : "$created"}, 60, 1000 ] } ] } ] } }  }, { "$project": { "y":{"$year":"$created"}, "m":{"$month":"$created"}, "d":{"$dayOfMonth":"$created"}, "h":{"$hour":"$created"}, "time":"$created" }  },  { "$group":{ "_id": { "year":"$y","month":"$m","day":"$d","hour":"$h"}, time:{"$first":"$time"},  "total":{ "$sum": 1}  }});
        def db = mongo.getDB(noSQLCollectionService.getDatabaseName())

        def result;
        def match
        //substract all minutes,seconds & milliseconds (last unit is hour)
        def projection1 = [$project : [ created : [$subtract:['$created', [$add : [[$millisecond : '$created'], [$multiply : [[$second : '$created'], 1000]], [$multiply : [[$minute : '$created'], 60*1000]] ]]]]]]
        def projection2 = [$project : [ y : [$year:'$created'], m : [$month:'$created'], d : [$dayOfMonth:'$created'], h : [$hour:'$created'], time : '$created']]
        def group = [$group : [_id : [ year: '$y', month: '$m', day: '$d', hour: '$h'], "time":[$first:'$time'], "frequency":[$sum:1]]]
        if(afterThan) {
            match = [$match : [$and : [[ created : [$gte : new Date(afterThan)]],[ project : project.id]]]]
        } else {
            match = [$match : [ project : project.id]]
        }

        result = db.persistentProjectConnection.aggregate(
                match,
                projection1,
                projection2,
                group
        )


        def connections = []
        result.results().each {

            // TODO evolve when https://jira.mongodb.org/browse/SERVER-6310 is resolved
            // as we groupBy hours in UTC, the GMT + xh30 have problems.

            /*def year = it["_id"]["year"]
            def month = it["_id"]["month"]
            def day = it["_id"]["day"]
            def hour = it["_id"]["hour"]*/
            def time = it["time"]
            def frequency = it["frequency"]


            /*Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.DAY_OF_MONTH, day);
            cal.set(Calendar.MONTH, month);*/

            connections << [/*year: year, month: month, day: day, hour: hour, */time : time, frequency: frequency]
        }
        result = connections
        return result
    }

    def countByProject(Project project, Long startDate = null, Long endDate = null) {
        def result = PersistentProjectConnection.createCriteria().get {
            eq("project", project)
            if(startDate) {
                gt("created", new Date(startDate))
            }
            if(endDate) {
                lt("created", new Date(endDate))
            }
            projections {
                rowCount()
            }
        }
        return [total: result]
    }

    def numberOfProjectConnections(String period, Long afterThan = null, Long beforeThan = null, Project project = null, User user = null){

        // what we want
        //db.persistentProjectConnection.aggregate( {"$match": {$and: [{project : ID_PROJECT}, {created : {$gte : new Date(AFTER) }}]}}, { "$project": { "created": {  "$subtract" : [  "$created",  {  "$add" : [  {"$millisecond" : "$created"}, { "$multiply" : [ {"$second" : "$created"}, 1000 ] }, { "$multiply" : [ {"$minute" : "$created"}, 60, 1000 ] } ] } ] } }  }, { "$project": { "y":{"$year":"$created"}, "m":{"$month":"$created"}, "d":{"$dayOfMonth":"$created"}, "h":{"$hour":"$created"}, "time":"$created" }  },  { "$group":{ "_id": { "year":"$y","month":"$m","day":"$d","hour":"$h"}, time:{"$first":"$time"},  "total":{ "$sum": 1}  }});
        def db = mongo.getDB(noSQLCollectionService.getDatabaseName())

        def match = [:]
        def projection1;
        def projection2;
        def group;
        def result;

        if(!period) period = "hour";

        switch (period){
            case "hour" :
                //substract all minutes,seconds & milliseconds (last unit is hour)
                projection1 = [$project : [ created : [$subtract:['$created', [$add : [[$millisecond : '$created'], [$multiply : [[$second : '$created'], 1000]], [$multiply : [[$minute : '$created'], 60*1000]] ]]]]]]
                projection2 = [$project : [ y : [$year:'$created'], m : [$month:'$created'], d : [$dayOfMonth:'$created'], h : [$hour:'$created'], time : '$created']]
                group = [$group : [_id : [ year: '$y', month: '$m', day: '$d', hour: '$h'], "time":[$first:'$time'], "frequency":[$sum:1]]]
                break;
            case "day" :
                //also substract hours (last unit is day)
                projection1 = [$project : [ created : [$subtract:['$created', [$add : [[$millisecond : '$created'], [$multiply : [[$second : '$created'], 1000]], [$multiply : [[$minute : '$created'], 60*1000]], [$multiply : [[$hour : '$created'], 60*60*1000]]]]]]]]
                projection2 = [$project : [ y : [$year:'$created'], m : [$month:'$created'], d : [$dayOfMonth:'$created'], time : '$created']]
                group = [$group : [_id : [ year: '$y', month: '$m', day: '$d'], "time":[$first:'$time'], "frequency":[$sum:1]]]
                break;
            case "week" :
                //also substract days (last unit is week)
                projection1 = [$project : [ created :[$subtract:['$created',[$add : [[$millisecond : '$created'], [$multiply : [[$second : '$created'], 1000]], [$multiply : [[$minute : '$created'], 60*1000]], [$multiply : [[$hour : '$created'], 60*60*1000]], [$multiply : [ [$subtract:[[$dayOfWeek : '$created'],1]], 24*60*60*1000]]]]]]]]
                projection2 = [$project : [ y : [$year:'$created'], m : [$month:'$created'], w : [$week:'$created'], time : '$created']]
                group = [$group : [_id : [ year: '$y', month: '$m', week: '$w'], "time":[$first:'$time'], "frequency":[$sum:1]]]
                break;
        }

        if(afterThan) {
            match = [ created : [$gte : new Date(afterThan)]]
        }
        if(beforeThan) {
            match = [$and: [match, [created: [$lte: new Date(beforeThan)]]]]
        }
        if(project){
            match = [$and: [match, [ project : project.id]]]
        }
        if(user){
            match = [$and: [match, [user: user.id]]]
        }
        match = [$match : match]

        result = db.persistentProjectConnection.aggregate(
                match,
                projection1,
                projection2,
                group
        )


        def connections = []
        result.results().each {
            // TODO evolve when https://jira.mongodb.org/browse/SERVER-6310 is resolved
            // as we groupBy hours in UTC, the GMT + xh30 have problems.

            def time = it["time"]
            def frequency = it["frequency"]

            connections << [time : time, frequency: frequency]
        }
        result = connections
        return result
    }

    def averageOfProjectConnections(Long afterThan = null, Long beforeThan = new Date().getTime(), String period, Project project = null, SecUser user = null){

        if(!afterThan){
            use(TimeCategory) {
                afterThan = (new Date(beforeThan) - 1.year).getTime();
            }
        }

        // what we want
        //db.persistentProjectConnection.aggregate( {"$match": {$and: [{project : ID_PROJECT}, {created : {$gte : new Date(AFTER) }}]}}, { "$project": { "created": {  "$subtract" : [  "$created",  {  "$add" : [  {"$millisecond" : "$created"}, { "$multiply" : [ {"$second" : "$created"}, 1000 ] }, { "$multiply" : [ {"$minute" : "$created"}, 60, 1000 ] } ] } ] } }  }, { "$project": { "y":{"$year":"$created"}, "m":{"$month":"$created"}, "d":{"$dayOfMonth":"$created"}, "h":{"$hour":"$created"}, "time":"$created" }  },  { "$group":{ "_id": { "year":"$y","month":"$m","day":"$d","hour":"$h"}, time:{"$first":"$time"},  "total":{ "$sum": 1}  }});
        def db = mongo.getDB(noSQLCollectionService.getDatabaseName())

        def match
        def projection1;
        def projection2;
        def group;
        def result;

        switch (period){
            case "hour" :
                //substract all minutes,seconds & milliseconds (last unit is hour)
                projection1 = [$project : [ created : [$subtract:['$created', [$add : [[$millisecond : '$created'], [$multiply : [[$second : '$created'], 1000]], [$multiply : [[$minute : '$created'], 60*1000]] ]]]]]]
                projection2 = [$project : [ h : [$hour:'$created'], time : '$created']]
                group = [$group : [_id : [ hour: '$h'], "time":[$first:'$time'], "frequency":[$sum:1]]]
                break;
            case "day" :
                //also substract hours (last unit is day)
                projection1 = [$project : [ created : [$subtract:['$created', [$add : [[$millisecond : '$created'], [$multiply : [[$second : '$created'], 1000]], [$multiply : [[$minute : '$created'], 60*1000]], [$multiply : [[$hour : '$created'], 60*60*1000]]]]]]]]
                projection2 = [$project : [ d : [$dayOfWeek:'$created'], time : '$created']]
                group = [$group : [_id : [ day: '$d'], "time":[$first:'$time'], "frequency":[$sum:1]]]
                break;
            case "week" :
                //also substract days (last unit is week)
                projection1 = [$project : [ created :[$subtract:['$created',[$add : [[$millisecond : '$created'], [$multiply : [[$second : '$created'], 1000]], [$multiply : [[$minute : '$created'], 60*1000]], [$multiply : [[$hour : '$created'], 60*60*1000]], [$multiply : [ [$subtract:[[$dayOfWeek : '$created'],1]], 24*60*60*1000]]]]]]]]
                projection2 = [$project : [ w : [$week:'$created'], time : '$created']]
                group = [$group : [_id : [ week: '$w'], "time":[$first:'$time'], "frequency":[$sum:1]]]
                break;
        }

        match = [[ created : [$gte : new Date(afterThan)]], [ created : [$lte : new Date(beforeThan)]]]
        if(project) match << [ project : project.id];
        if(user) match << [ user : user.id];
        match = [$match : [$and : match]]

        result = db.persistentProjectConnection.aggregate(
                match,
                projection1,
                projection2,
                group
        ).results()

        def connections = []

        int total = result.sum{it.frequency}
        if(total == 0) total = 1

        result.each {
            // TODO evolve when https://jira.mongodb.org/browse/SERVER-6310 is resolved
            // as we groupBy hours in UTC, the GMT + xh30 have problems.

            def time = it["time"]
            def frequency = (it["frequency"])/total

            connections << [time : time, frequency: frequency]
        }
        return connections
    }

    def getUserActivityDetails(Long activityId){
        PersistentProjectConnection connection = PersistentProjectConnection.read(activityId)
        Project project = Project.read(connection.project)
        securityACLService.check(project,WRITE)

        def consultations = PersistentImageConsultation.findAllByCreatedGreaterThanAndProjectConnection(connection.created, activityId, [sort: 'created', order: 'desc'])

        if(consultations.size() == 0) return consultations;
        // current connection. We need to calculate time for the currently opened image
        if(!connection.time) {
            int i = 0;
            Date before = new Date()
            while(i < consultations.size() && !consultations[i].time) {
                consultations[i] = ((PersistentImageConsultation) consultations[i]).clone()
                imageConsultationService.fillImageConsultation(consultations[i], before)
                before = consultations[i].created
                i++
            }
        }
        return consultations
    }
}
