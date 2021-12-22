package be.cytomine.image

import be.cytomine.command.AddCommand
import be.cytomine.command.Command
import be.cytomine.command.EditCommand
import be.cytomine.command.Transaction
import be.cytomine.ontology.AnnotationIndex
import be.cytomine.ontology.AnnotationTrack
import be.cytomine.project.Project
import be.cytomine.security.SecUser
import be.cytomine.utils.ModelService
import be.cytomine.utils.Task
import grails.converters.JSON
import org.hibernate.FetchMode

import static org.springframework.security.acls.domain.BasePermission.READ

class SliceInstanceService extends ModelService {

    static transactional = true

    def cytomineService
    def securityACLService
    def dataSource

    def currentDomain() {
        return SliceInstance
    }

    def read(def id) {
        SliceInstance slice = SliceInstance.read(id)
        if (slice) {
            securityACLService.check(slice.container(), READ)
        }
        slice
    }

    def read(ImageInstance image, double c, double z, double t) {
        SliceInstance slice = SliceInstance.createCriteria().get {
            createAlias("baseSlice", "as")
            eq("image", image)
            eq("as.channel", c)
            eq("as.zStack", z)
            eq("as.time", t)
        }
        if (slice) {
            securityACLService.check(slice.container(), READ)
        }
        slice
    }

    def list(ImageInstance image, String sortedProperty = null, String sortDirection = null, Long max  = 0, Long offset = 0, searchParameters = []) {
        securityACLService.check(image, READ)
        def validSearchParameters = getDomainAssociatedSearchParameters(SliceInstance, searchParameters)

        Closure sorting = {
            order("as.time", "asc")
            order("as.zStack", "asc")
            order("as.channel", "asc")
        }

        criteriaRequestWithPagination(SliceInstance, max, offset, {
                createAlias("baseSlice", "as")
                eq("image", image)
                fetchMode("baseSlice", FetchMode.JOIN)
            }, validSearchParameters, sortedProperty, sortDirection, sorting)
    }

    def add(def json) {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkUser(currentUser)
        securityACLService.check(json.project, Project,READ)
        securityACLService.checkisNotReadOnly(json.project,Project)

        Command c = new AddCommand(user: currentUser)
        executeCommand(c, null, json)
    }

    def update(SliceInstance slice, def json) {
        securityACLService.check(slice.container(),READ)
        securityACLService.check(json.project,Project,READ)
//        securityACLService.checkFullOrRestrictedForOwner(slice.container(),slice.user)
        securityACLService.checkisNotReadOnly(slice.container())
        securityACLService.checkisNotReadOnly(json.project,Project)
        SecUser currentUser = cytomineService.getCurrentUser()

        Command c = new EditCommand(user: currentUser)
        executeCommand(c, slice, json)
    }

    def delete(SliceInstance slice, Transaction transaction = null, Task task = null, boolean printMessage = true) {
        securityACLService.check(slice.container(),READ)
        securityACLService.checkisNotReadOnly(slice.container())
        SecUser currentUser = cytomineService.getCurrentUser()
        def jsonNewData = JSON.parse(slice.encodeAsJSON())
        log.debug jsonNewData
        jsonNewData.deleted = new Date().time
        Command c = new EditCommand(user: currentUser)
        c.delete = true
        return executeCommand(c,slice,jsonNewData)
    }

    def annotationTrackService
    def deleteDependentAnnotationTrack(SliceInstance slice, Transaction transaction, Task task = null) {
        AnnotationTrack.findAllBySlice(slice).each {
            annotationTrackService.delete(it, transaction, task)
        }
    }

    def annotationIndexService
    def deleteDependentAnnotationIndex(SliceInstance slice, Transaction transaction, Task task = null) {
        AnnotationIndex.findAllBySlice(slice).each {
            it.delete()
        }
    }

    def getStringParamsI18n(Object domain) {
        return [domain.id, domain.baseSlice.channel, domain.baseSlice.zStack, domain.baseSlice.time]
    }
}
