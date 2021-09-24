package be.cytomine.utils.bootstrap

import be.cytomine.image.AbstractImage
import be.cytomine.image.AbstractSlice
import be.cytomine.image.CompanionFile
import be.cytomine.image.ImageInstance
import be.cytomine.image.Mime
import be.cytomine.image.SliceInstance
import be.cytomine.image.group.ImageGroup
import be.cytomine.image.multidim.ImageGroupHDF5
import be.cytomine.image.multidim.ImageSequence
import be.cytomine.meta.AttachedFile
import be.cytomine.middleware.ImageServer

/*
* Copyright (c) 2009-2019. Authors: see NOTICE file.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import be.cytomine.image.server.Storage
import be.cytomine.image.server.StorageAbstractImage
import be.cytomine.image.UploadedFile
import be.cytomine.middleware.AmqpQueue
import be.cytomine.meta.Property
import be.cytomine.ontology.Track
import be.cytomine.processing.ImageFilter
import be.cytomine.project.Project
import be.cytomine.project.ProjectLastActivity
import be.cytomine.security.SecRole
import be.cytomine.security.SecUser
import be.cytomine.security.SecUserSecRole
import be.cytomine.security.User
import be.cytomine.meta.Configuration
import be.cytomine.utils.Version
import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityUtils
import groovy.sql.Sql
import org.apache.commons.io.FilenameUtils

/**
 * Cytomine
 * User: lrollus
 * This class contains all code when you want to change the database dataset.
 * E.g.: add new rows for a specific version, drop a column, ...
 *
 * The main method ("execChangeForOldVersion") is called by the bootstrap.
 * This method automatically run all initYYYYMMDD() methods from this class where YYYYMMDD is lt version number
 *
 * E.g. init20150115() will be call if the current version is init20150201.
 * init20150101() won't be call because: 20150101 < 20150115 < 20150201.
 *
 * At the end of the execChangeForOldVersion, the current version will be set thanks to the grailsApplication.metadata.'app.version' config
 */
class BootstrapOldVersionService {

    def grailsApplication
    def bootstrapUtilsService
    def dataSource
    def storageService
    def tableService
    def mongo
    def noSQLCollectionService
    def executorService
    def bootstrapDataService


    void execChangeForOldVersion() {
        def methods = this.metaClass.methods*.name.sort().unique()
        Version version = Version.getLastVersion()

        if(!version.major){
            methods.findAll { it =~ "init[0-9]" }.each { method ->
                Long methodDate = Long.parseLong(method.replace("init", ""))
                if (methodDate > version.number) {
                    log.info "Run code for version > $methodDate"
                    this."init$methodDate"()
                } else {
                    log.info "Skip code for $methodDate"
                }
            }

            version.major = 0
            version.minor = 0
            version.patch = 0
        }
        methods.findAll { it.startsWith("initv") }.each { method ->

            method = method.substring("initv".size())

            Short major = Short.parseShort(method.split("_")[0])
            Short minor = Short.parseShort(method.split("_")[1])
            Short patch = Short.parseShort(method.split("_")[2])

            if(major > version.major || (major == version.major && minor > version.minor)
                    || (major == version.major && minor == version.minor && patch > version.patch)) {
                log.info "Run code for v${method.replace("_",".")} update"
                this."initv$method"()
            } else {
                log.info "Skip code for initv$method"
            }

        }
        Version.setCurrentVersion(Long.parseLong(grailsApplication.metadata.'app.versionDate'), grailsApplication.metadata.'app.version')
    }

    def initv2_4_0() {
        log.info "Migration to v2.4"
        def sql = new Sql(dataSource)
        sql.executeUpdate("UPDATE image_filter SET available = true WHERE available IS NULL;")
        bootstrapUtilsService.updateSqlColumnConstraint("image_filter", "available", "DROP NOT NULL")
        sql.close()

        // Delete forever histograms stored in database. This feature was experimental and is replaced by PIMS.
        // However if we delete data from DB, it is no more possible to retrograde to IMS
        // TODO: discuss about the right strategy.
        //sql.executeUpdate("DROP TABLE sample_histogram;")

        bootstrapDataService.initImageFilters()
    }

    def initv2_2_1() {
        log.info "Migration to v2.2.1"
        def sql = new Sql(dataSource)
        sql.executeUpdate("UPDATE abstract_image SET tile_size = 256 WHERE tile_size IS NULL;")
        bootstrapUtilsService.updateSqlColumnConstraint("abstract_image", "tile_size", "SET DEFAULT 256")
        sql.close()
    }

    def initv2_2_0() {
        log.info "Migration to V2.2.0"

        log.info "Migrate attached files from DB to file system"
        bootstrapUtilsService.updateSqlColumnConstraint("attached_file", "data", "DROP NOT NULL")

        // We need to fetch attached files one by one to avoid OOM error as all attached files content would be retrieved
        // the database in a single request !
        AttachedFile af = AttachedFile.findByDataIsNotNull()
        while (af != null) {
            af.getFile().withOutputStream {
                it.write(af.data)
            }
            af.data = null
            af.save()
            af = AttachedFile.findByDataIsNotNull()
        }

        // For next version:
        //bootstrapUtilsService.dropSqlColumn("attached_file", "data")
    }

    def initv2_0_0() {
        log.info "Migration to V2.0.0"
        def sql = new Sql(dataSource)

        /****** USERS ******/
        log.info "Migration of users"

        log.info "Users: Add new column isDeveloper"
        sql.executeUpdate("UPDATE sec_user SET is_developer = FALSE WHERE is_developer IS NULL;")
        bootstrapUtilsService.updateSqlColumnConstraint("sec_user", "is_developer", "SET DEFAULT FALSE")

        log.info "Users: Add new column language"
        sql.executeUpdate("UPDATE sec_user SET language = 'ENGLISH' WHERE language IS NULL;")
        bootstrapUtilsService.updateSqlColumnConstraint("sec_user", "language", "SET DEFAULT 'ENGLISH'")
//        bootstrapUtilsService.updateSqlColumnConstraint("sec_user", "language", "SET NOT NULL")

        log.info "Users: Add new column origin"
        def systemUsers = ['ImageServer1', 'superadmin', 'admin', 'rabbitmq', 'monitoring']
        User.findAllByUsernameInList(systemUsers).each { systemUser ->
            systemUser.origin = "SYSTEM"
            systemUser.save()
        }
        sql.executeUpdate("UPDATE sec_user SET origin = 'BOOTSTRAP' WHERE origin IS NULL;")

        log.info "Users: Remove no more used columns"
        bootstrapUtilsService.dropSqlColumn("sec_user", "skype_account")
        bootstrapUtilsService.dropSqlColumn("sec_user", "sip_account")


        /******* PROJECT ******/
        log.info "Migration of projects"

        log.info "Projects: Allow projects without ontology"
        bootstrapUtilsService.updateSqlColumnConstraint("project", "ontology_id", "DROP NOT NULL")

        if (ProjectLastActivity.count() == 0) {
            log.info "Projects: Populate project last activity table"
            def values = []
            sql.eachRow("SELECT project_id, MAX(created) AS date FROM command_history WHERE project_id IS NOT NULL GROUP BY project_id") {
                values << [id: "nextval('hibernate_sequence')", version: 0, project_id: it.project_id, last_activity: "'${it.date}'"]
            }

            def batchSize = 100
            def fields = ["id", "version", "project_id", "last_activity"]
            def groups = values.collate(batchSize)
            groups.eachWithIndex { def vals, int i ->
                def formatted = vals.collect { v -> "(" + fields.collect { f -> v[f] }.join(",") + ")"}
                sql.execute('INSERT INTO project_last_activity (' + fields.join(",") +') VALUES ' + formatted.join(",") + " ON CONFLICT DO NOTHING;")
            }
        }


        /******* SOFTWARE ******/
        log.info "Migration of software"
        bootstrapUtilsService.dropSqlColumnUniqueConstraint("software")


        /******* JOB ******/
        log.info "Migration of jobs"

        log.info "Jobs: Add new column favorite"
        sql.executeUpdate("UPDATE job SET favorite = FALSE WHERE favorite IS NULL;")
        bootstrapUtilsService.updateSqlColumnConstraint("job", "favorite", "SET DEFAULT FALSE")

        log.info("Jobs: Update attached files names of job logs to be displayed in webUI")
        sql.executeUpdate("UPDATE attached_file SET filename = 'log.out' " +
                "WHERE domain_class_name = 'be.cytomine.processing.Job' AND filename LIKE '%.out';")


        /****** CONFIGURATIONS ******/
        log.info "Migration of configurations"
        List<Configuration> configurations = Configuration.findAllByKeyLike("%.%")
        for(int i = 0; i<configurations.size(); i++){
            configurations[i].key = configurations[i].key.replace(".","_")
            configurations[i].save()
        }
        bootstrapUtilsService.createConfigurations(true)


        /****** STORAGE ******/
        log.info "Migration of storages"

        // Move old Long[] storages to one storage (only first one is kept)
        if (bootstrapUtilsService.checkSqlColumnExistence('uploaded_file', 'storages')) {
            log.info "Storage: Update storage references in uploaded_file"
            sql.eachRow("SELECT id, storages FROM uploaded_file") {
                def ufId = it[0]
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(it[1] as byte[]))
                Long[] storages = (Long[]) ois.readObject()
                sql.executeUpdate("UPDATE uploaded_file SET storage_id = ${storages.first()} WHERE id = ${ufId};")
                ois.close()
            }
            bootstrapUtilsService.dropSqlColumn("uploaded_file", "storages")
        }

        log.info "Storage: Remove no more used column"
        bootstrapUtilsService.dropSqlColumn("storage", "base_path")


        /****** IMAGE SERVER ******/
        log.info "Migration of image servers"

        log.info "Image server: Update image server reference in uploaded_file"
        //TODO: use old ImageServerStorage and StorageAbstractImage
        //TODO: manage all old image servers.
        def server = ImageServer.first()
        UploadedFile.executeUpdate("update UploadedFile uf set uf.imageServer = ? where uf.imageServer is null",
                [server])

        log.info "Image server: Update base path in image_server with known base path"
        ImageServer.executeUpdate("update ImageServer i set i.basePath = ? where i.basePath is null",
                [grailsApplication.config.storage_path])

        log.info "Image server: Remove no more used columns"
        bootstrapUtilsService.dropSqlColumn("image_server", "service")
        bootstrapUtilsService.dropSqlColumn("image_server", "class_name")


        /****** UPLOADED FILE (1) ******/
        if (bootstrapUtilsService.checkSqlColumnExistence('uploaded_file', 'image_id')) {
            log.info "Migration of uploaded files (1)"

            log.info "Set uploaded files with valid images and status to 'uploaded' as 'deployed'"
            sql.executeUpdate("UPDATE uploaded_file SET status = 2 WHERE image_id IS NOT NULL and status = 0;");

            log.info "Remove erroneous uploaded files (filename duplicates)"
            sql.executeUpdate("DELETE FROM uploaded_file WHERE size > 0 AND parent_id IS NOT NULL AND image_id IN (SELECT image_id FROM uploaded_file GROUP BY image_id, size HAVING count(*) = 2);")
            sql.executeUpdate("DELETE FROM uploaded_file WHERE size = 0 AND image_id IN (SELECT image_id FROM uploaded_file GROUP BY image_id HAVING COUNT(*) = 2);")
        }


        /****** ABSTRACT SLICE ******/
        if (AbstractSlice.count() == 0) {
            log.info "Migration of abstract slice"

            log.info "Abstract slice: Add (0,0,0) abstract slice for all abstract images"
            def values = []
            sql.eachRow("select uploaded_file.id, image_id, mime_id, abstract_image.created " +
                    "from uploaded_file " +
                    "left join abstract_image on abstract_image.id = uploaded_file.image_id " +
                    "where image_id is not null") {
                values << [
                        id: "nextval('hibernate_sequence')",
                        created: "'${it.created}'",
                        version: 0,
                        image_id: it.image_id,
                        uploaded_file_id: it.id,
                        mime_id: it.mime_id,
                        channel: 0,
                        z_stack: 0,
                        time: 0
                ]
            }

            def batchSize = 2000
            def fields = ["id", "created", "version", "image_id", "uploaded_file_id", "mime_id", "channel", "z_stack", "time"]
            def groups = values.collate(batchSize)
            groups.eachWithIndex { def vals, int i ->
                def formatted = vals.collect { v -> "(" + fields.collect { f -> v[f] }.join(",") + ")"}
                sql.execute('INSERT INTO abstract_slice (' + fields.join(",") + ') VALUES ' + formatted.join(",") + ';')
                log.info "- Inserted ${i * batchSize} elements ($i / ${groups.size()})"
            }
        }


        /****** SLICE INSTANCE ******/
        if (SliceInstance.count() == 0) {
            log.info "Migration of slice instances"

            log.info "Slice instance: Add (0,0,0) slice instance for all image instances which are not in an image group"
            def values = []
            sql.eachRow("SELECT image_instance.id as iiid, abstract_slice.id as asid, image_instance.project_id as pid, " +
                    "image_instance.created " +
                    "from image_instance " +
                    "left join abstract_image on abstract_image.id = image_instance.base_image_id " +
                    "left join abstract_slice on abstract_slice.image_id = abstract_image.id " +
                    "left join image_sequence on image_sequence.image_id = image_instance.id " +
                    "where abstract_slice.channel = 0 and abstract_slice.z_stack = 0 and abstract_slice.time = 0 " +
                    "and image_sequence.id is null;") {
                values << [
                        id: "nextval('hibernate_sequence')",
                        created: "'${it.created}'",
                        version: 0,
                        base_slice_id: it.asid,
                        image_id: it.iiid,
                        project_id: it.pid
                ]
            }

            def batchSize = 2000
            def fields = ["id", "created", "version", "base_slice_id", "image_id", "project_id"]
            def groups = values.collate(batchSize)
            groups.eachWithIndex { def vals, int i ->
                def formatted = vals.collect { v -> "(" + fields.collect { f -> v[f] }.join(",") + ")"}
                sql.execute('INSERT INTO slice_instance (' + fields.join(",") + ') VALUES ' + formatted.join(",") + ';')
                log.info "- Inserted ${i * batchSize} elements ($i / ${groups.size()})"
            }
        }


        /****** ABSTRACT IMAGE ******/
        log.info "Migration of abstract images"

        if (bootstrapUtilsService.checkSqlColumnExistence("abstract_image", "physical_sizex")) {
            new Sql(dataSource).executeUpdate("UPDATE abstract_image SET physical_size_x = physical_sizex;")
            new Sql(dataSource).executeUpdate("UPDATE abstract_image SET physical_size_y = physical_sizey;")
            new Sql(dataSource).executeUpdate("UPDATE abstract_image SET physical_size_z = physical_sizez;")
            bootstrapUtilsService.dropSqlColumn("abstract_image", "physical_sizex")
            bootstrapUtilsService.dropSqlColumn("abstract_image", "physical_sizey")
            bootstrapUtilsService.dropSqlColumn("abstract_image", "physical_sizez")
        }

        log.info "Abstract image: update new fields depth, duration and channels"
        sql.executeUpdate("update abstract_image set depth = 1 where depth is null;")
        sql.executeUpdate("update abstract_image set duration = 1 where duration is null;")
        sql.executeUpdate("update abstract_image set channels = 1 where channels is null;")

        log.info "Abstract image: remove no more used columns"
        bootstrapUtilsService.dropSqlColumnUniqueConstraint("abstract_image")
        bootstrapUtilsService.dropSqlColumn("abstract_image", "filename")

        bootstrapUtilsService.dropSqlColumn("abstract_image", "path")

        log.info "Abstract image: Remove no more used DB cached thumbs"
        sql.executeUpdate("delete from attached_file where domain_class_name = 'be.cytomine.image.AbstractImage' and filename like '/image/thumb%';")


        /****** IMAGE INSTANCE ******/
        if (bootstrapUtilsService.checkSqlColumnExistence("image_instance", "physical_sizex")) {
            log.info "Migration of image instances"
            new Sql(dataSource).executeUpdate("UPDATE image_instance SET physical_size_x = physical_sizex;")
            new Sql(dataSource).executeUpdate("UPDATE image_instance SET physical_size_y = physical_sizey;")
            new Sql(dataSource).executeUpdate("UPDATE image_instance SET physical_size_z = physical_sizez;")
            new Sql(dataSource).executeUpdate("ALTER TABLE image_instance DROP COLUMN physical_sizex CASCADE;")
            new Sql(dataSource).executeUpdate("ALTER TABLE image_instance DROP COLUMN physical_sizey CASCADE;")
            new Sql(dataSource).executeUpdate("ALTER TABLE image_instance DROP COLUMN physical_sizez CASCADE;")
        }


        /****** IMAGE GROUP ******/
        def imageInstancesFromImageGroupToSlices = [:]
        def abstractImagesFromImageGroupToSlices = [:]
        def imageGroupsToImageInstances = [:]

        bootstrapUtilsService.updateSqlColumnConstraint("uploaded_file", "converted", "DROP NOT NULL")
        bootstrapUtilsService.updateSqlColumnConstraint("uploaded_file", "path", "DROP NOT NULL")
        bootstrapUtilsService.updateSqlColumnConstraint("abstract_image", "mime_id", "DROP NOT NULL")

        if (ImageGroup.count() > 0) {
            log.info "Migration of image groups"
            ImageGroup.findAll().each { group ->
                log.info "Image group: Convert group ${group.name}"
                def sequences = ImageSequence.findAllByImageGroup(group)
                if (sequences.size() == 0) {
                    log.info "Empty image group skipped"
                    return
                }
                def duration = sequences.collect { it.time }.unique().size()
                def depth = sequences.collect { it.zStack }.unique().size()
                def channels = sequences.collect { it.channel }.unique().size()
                def width = sequences[0].image.baseImage.width
                def height = sequences[0].image.baseImage.height
                def user = sequences[0].image.user
                Storage storage = Storage.findByUser(user)
                UploadedFile uf = new UploadedFile(originalFilename: group.name, filename: group.name,
                        user: user, storage: storage,
                        ext: "virt", imageServer: server, path: "/", converted: true,
                        contentType: "virtual/stack", size: 0, status: 100).save(flush: true, failOnError: true)

                def image = new AbstractImage(uploadedFile: uf, originalFilename: uf.originalFilename, user: user,
                        duration: duration, depth: depth, channels: channels, width: width, height: height)
                image.save(failOnError: true, flush: true)

                def project = group.project
                def imageInstance = new ImageInstance(baseImage: image, project: project, user: user)
                imageInstance.save(failOnError: true, flush: true)

                imageGroupsToImageInstances << [(group.id): imageInstance.id]

                def hdf5 = ImageGroupHDF5.findByGroupAndStatus(group, 3)
                if (hdf5) {
                    def hdf5Filename = hdf5.filename.split("/")[-1]?.trim()
                    def profileUf = new UploadedFile(originalFilename: "profile.hdf5", filename: hdf5Filename, parent: uf,
                            user: user, storage: storage, ext: "hdf5", imageServer: server, path: "/", converted: true,
                            contentType: "application/x-hdf5", size: 0, status: 100).save(flush: true, failOnError: true)
                    new CompanionFile(uploadedFile: profileUf, image: image,
                            originalFilename: "profile.hdf5", filename: "profile.hdf5", type: "HDF5").save(flush: true, failOnError: true)
                }

                sql.executeUpdate("update attached_file set domain_class_name = 'be.cytomine.image.ImageInstance', " +
                        "domain_ident = ${imageInstance.id} where domain_ident = ${group.id}")

                sql.eachRow("SELECT ai.created, uf.id as ufid, ai.mime_id, seq.channel, seq.z_stack, seq.time, " +
                        "seq.image_id as iiid , ai.id as aiid " +
                        "FROM image_sequence seq " +
                        "LEFT JOIN image_instance ii ON seq.image_id = ii.id " +
                        "LEFT JOIN abstract_image ai ON ii.base_image_id = ai.id " +
                        "LEFT JOIN uploaded_file uf ON ai.id = uf.image_id " +
                        "WHERE seq.image_group_id = :group", [group: group.id]) {
                    // 1) create abstract slice
                    def absSlice = sql.executeInsert("INSERT INTO abstract_slice(id, created, version, image_id, uploaded_file_id," +
                            " mime_id, channel, z_stack, time) VALUES " +
                            "(nextval('hibernate_sequence'), '${it.created}', 0, ${image.id}, ${it.ufid}, " +
                            "${it.mime_id}, ${it.channel}, ${it.z_stack}, ${it.time})")
                    def absSliceId = absSlice[0][0]
                    abstractImagesFromImageGroupToSlices << [(it.aiid): absSliceId]

                    // 2) create slice_instance
                    def slice = sql.executeInsert("INSERT INTO slice_instance(id, created, version, project_id, " +
                            "image_id, base_slice_id) VALUES " +
                            "(nextval('hibernate_sequence'), '${it.created}', 0, ${project.id}, " +
                            "${imageInstance.id}, ${absSliceId})")
                    def sliceId = slice[0][0]
                    imageInstancesFromImageGroupToSlices << [(it.iiid): [slice: sliceId, image:imageInstance.id]]
                }
            }
        }

        bootstrapUtilsService.dropSqlColumn("abstract_image", "mime_id")


        /****** UPLOADED FILE ******/
        log.info "Migration of uploaded files (2)"
        if (bootstrapUtilsService.checkSqlColumnExistence('uploaded_file', 'image_id')) {
            log.info("Uploaded file: Change direction of UF - AI relation and use the root as AI uploaded file")
            sql.executeUpdate("update abstract_image " +
                    "set uploaded_file_id = cast(ltree2text(subltree(uploaded_file.l_tree, 0, 1)) as bigint) " +
                    "from uploaded_file " +
                    "where abstract_image.id = image_id " +
                    "and uploaded_file_id is null " +
                    "and cast(ltree2text(subltree(uploaded_file.l_tree, 0, 1)) as bigint) IN (SELECT id FROM uploaded_file);")
        }

        log.info "Uploaded file: Remove no more used columns"
        bootstrapUtilsService.dropSqlColumn("uploaded_file", "image_id")
        bootstrapUtilsService.dropSqlColumn("uploaded_file", "path")
        bootstrapUtilsService.dropSqlColumn("uploaded_file", "converted")

        log.info "Uploaded file: Migrate to new status"
        [[old: 1, "new": 104],
         [old: 2, "new": 100],
         [old: 3, "new": 11],
         [old: 4, "new": 31],
         [old: 5, "new": 20],
         [old: 6, "new": 40],
         [old: 7, "new": 20],
         [old: 8, "new": 41],
         [old: 9, "new": 41]].each {
            sql.executeUpdate("UPDATE uploaded_file SET status = ${it["new"]} WHERE status = ${it["old"]}")
        }


        /****** ANNOTATIONS ******/
        log.info "Migration of annotations"
        imageInstancesFromImageGroupToSlices.each { o, n ->
            log.info "Update annotation references to slices for old image instance $o that was linked to image group"
            sql.executeUpdate("update algo_annotation set image_id = ${n.image}, slice_id = ${n.slice} where image_id = ${o};")
            sql.executeUpdate("update user_annotation set image_id = ${n.image}, slice_id = ${n.slice} where image_id = ${o};")
            sql.executeUpdate("update reviewed_annotation set image_id = ${n.image}, slice_id = ${n.slice} where image_id = ${o};")
            sql.executeUpdate("update roi_annotation set image_id = ${n.image}, slice_id = ${n.slice} where image_id = ${o};")
            sql.executeUpdate("update annotation_index set image_id = ${n.image}, slice_id = ${n.slice} where image_id = ${o};")
        }

        log.info "Update algo annotation references to slices for 2D images"
        sql.executeUpdate("update algo_annotation " +
                "set slice_id = slice.id " +
                "from slice_instance slice " +
                "where slice.image_id = algo_annotation.image_id " +
                "and slice_id IS NULL")

        log.info "Update user annotation references to slices for 2D images"
        sql.executeUpdate("update user_annotation " +
                "set slice_id = slice.id " +
                "from slice_instance slice " +
                "where slice.image_id = user_annotation.image_id " +
                "and slice_id IS NULL")

        log.info "Update reviewed annotation references to slices for 2D images"
        sql.executeUpdate("update reviewed_annotation " +
                "set slice_id = slice.id " +
                "from slice_instance slice " +
                "where slice.image_id = reviewed_annotation.image_id " +
                "and slice_id IS NULL")

        log.info "Update ROI annotation references to slices for 2D images"
        sql.executeUpdate("update roi_annotation " +
                "set slice_id = slice.id " +
                "from slice_instance slice " +
                "where slice.image_id = roi_annotation.image_id " +
                "and slice_id IS NULL")


        /****** ANNOTATION INDEX ******/
        if (bootstrapUtilsService.checkSqlColumnExistence('annotation_index', 'image_id')) {
            log.info "Migration of annotation indexes"
            sql.executeUpdate("update annotation_index " +
                    "set slice_id = slice.id " +
                    "from slice_instance slice " +
                    "where slice.image_id = annotation_index.image_id " +
                    "and slice_id IS NULL")

            log.info "Annotation index: Remove no more used column"
            bootstrapUtilsService.dropSqlColumn("annotation_index", "image_id")
        }


        /****** MIME ******/
        log.info "Migration of mime types"

        log.info "Mime type: Update mime reference"
        def pyrTiffMime = Mime.findByMimeType("image/pyrtiff")
        def mimeToRemove = ["image/tiff", "image/tif", "zeiss/zvi"]
        mimeToRemove.each {
            def mime = Mime.findByMimeType(it)
            if (mime) {
                sql.executeUpdate("UPDATE abstract_slice SET mime_id = ${pyrTiffMime.id} WHERE mime_id = ${mime.id}")
                sql.executeUpdate("DELETE FROM mime_image_server WHERE mime_id = ${mime.id}")
                mime.delete()
            }
        }


        /****** CLEANING ******/
        log.info "Cleaning: Remove no more used files"

        if (imageInstancesFromImageGroupToSlices.size() > 0) {
            log.info "Cleaning: Delete old image instances used in image groups"
            sql.executeUpdate("DELETE FROM image_sequence " +
                    ('where image_id IN (' + imageInstancesFromImageGroupToSlices.keySet().join(',') + ')'))
            sql.executeUpdate("DELETE FROM image_instance " +
                    ('where id IN (' + imageInstancesFromImageGroupToSlices.keySet().join(',') + ')'))
            sql.executeUpdate("DELETE FROM image_grouphdf5;")
            sql.executeUpdate("DELETE FROM image_group;")
        }

        log.info "Cleaning: Delete old image references"
        sql.executeUpdate("DELETE FROM storage_abstract_image;")
//        sql.executeUpdate("delete from image_instance where id not in (select image_id from slice_instance);")
//        sql.executeUpdate("delete from abstract_image where id not in (select image_id from abstract_slice);")
        sql.executeUpdate("UPDATE image_instance ii SET deleted = NOW() WHERE NOT EXISTS(SELECT 1 FROM slice_instance si WHERE si.image_id = ii.id);")
        sql.executeUpdate("UPDATE abstract_image ai SET deleted = NOW() WHERE NOT EXISTS(SELECT 1 FROM abstract_slice asl WHERE asl.image_id = ai.id);")

        /****** COUNTERS ******/
        log.info "Migration of counters"

        log.info "Counters: recompute project counters"
        sql.executeUpdate("UPDATE project p SET " +
                "count_images = (select count(*) from image_instance ii where ii.deleted is null and ii.project_id = p.id), " +
                "count_annotations = (select count(*) from user_annotation ua left join image_instance ii on ii.id = ua.image_id where ua.deleted is null and ua.project_id = p.id and ii.deleted is null), " +
                "count_job_annotations = (select count(*) from algo_annotation aa left join image_instance ii on ii.id = aa.image_id where aa.deleted is null and ii.deleted is null and aa.project_id = p.id), " +
                "count_reviewed_annotations = (select count(*) from reviewed_annotation ra left join image_instance ii on ii.id = ra.image_id where ra.deleted is null and ii.deleted is null and ra.project_id = p.id);")

        log.info "Counters: recompute image counters"
        sql.executeUpdate("UPDATE image_instance ii SET " +
                "count_image_annotations = (select count(*) from user_annotation ua where ua.deleted is null and ua.image_id = ii.id), " +
                "count_image_job_annotations = (select count(*) from algo_annotation aa where aa.deleted is null and aa.image_id = ii.id), " +
                "count_image_reviewed_annotations = (select count(*) from reviewed_annotation ra where ra.deleted is null and ra.image_id = ii.id);")


        /****** TRACKS *******/
        if (Track.count() == 0) {
            log.info "Migration of tracks"

            def lastImageId = 0
            def lastGroupId = -1
            def trackId = 0
            sql.eachRow("select p.created, domain_ident, image_id, slice_id, key, value, " +
                    "(select value from property pp where pp.domain_ident = p.domain_ident " +
                    "and pp.key = 'CUSTOM_ANNOTATION_DEFAULT_COLOR') as color, project_id \n" +
                    "from property p\n" +
                    "join algo_annotation a on a.id = p.domain_ident\n" +
                    "where p.key = 'ANNOTATION_GROUP_ID'\n" +
                    "order by image_id asc, value asc;") {
                if (it.image_id != lastImageId || it.value != lastGroupId ) {
                    lastImageId = it.image_id
                    lastGroupId = it.value
                    log.info "Track: Add track ${lastGroupId} for image ${lastImageId}"
                    trackId = sql.executeInsert("INSERT INTO track(id, created, version, name, color, image_id, project_id)" +
                            "VALUES (nextval('hibernate_sequence'), '${it.created}', 0, 'Track #${it.value}', " +
                            "'${it.color}', ${it.image_id}, ${it.project_id});")[0][0]
                }

                sql.executeInsert("INSERT INTO annotation_track(id, created, version, annotation_class_name, " +
                        "annotation_ident, track_id, slice_id) VALUES " +
                        "(nextval('hibernate_sequence'), '${it.created}', 0, 'be.cytomine.ontology.AlgoAnnotation', " +
                        "${it.domain_ident}, ${trackId}, ${it.slice_id});")
            }
        }

        /****** DESCRIPTION ******/
        //TODO
//        log.info("Update reference of attached files that are used in description (only for project)")
//        sql.executeUpdate("update attached_file set domain_class_name = 'be.cytomine.utils.Description', " +
//                "domain_ident = description.id " +
//                "from description " +
//                "where attached_file.domain_ident = description.domain_ident " +
//                "and attached_file.domain_class_name = 'be.cytomine.project.Project';")


        /****** VIEWS ******/
        log.info "Regeneration of DB views"
        sql.executeUpdate("DROP VIEW user_image;")
        tableService.initTable()

        sql.close()
    }

    def imagePropertiesService

    void init20180904() {
        log.info "20180904"

        boolean exists = new Sql(dataSource).rows("SELECT column_name " +
                "FROM information_schema.columns " +
                "WHERE table_name='version' and column_name='major';").size() == 1;
        if (!exists) {
            new Sql(dataSource).executeUpdate("ALTER TABLE version ADD COLUMN major integer;")
            new Sql(dataSource).executeUpdate("ALTER TABLE version ADD COLUMN minor integer;")
            new Sql(dataSource).executeUpdate("ALTER TABLE version ADD COLUMN patch integer;")
        }
    }

    void init20180701() {
        log.info "20180701"

        boolean exists = new Sql(dataSource).rows("SELECT column_name " +
                "FROM information_schema.columns " +
                "WHERE table_name='configuration' and column_name='reading_role';").size() == 1;
        if (!exists) {
            new Sql(dataSource).executeUpdate("ALTER TABLE configuration ADD COLUMN reading_role varchar(255) NOT NULL DEFAULT 'ADMIN';")

            String request = "SELECT id FROM configuration;"
            def sql = new Sql(dataSource)
            def data = []
            sql.eachRow(request) {
                data << it[0]
            }
            sql.close()
            if(data.size() > 0) new Sql(dataSource).executeUpdate("UPDATE configuration SET reading_role = 'ADMIN' WHERE id IN (" + data.join(",") + ");")

            request = "SELECT id FROM configuration WHERE reading_role_id = "+SecRole.findByAuthority("ROLE_USER").id+";"
            sql = new Sql(dataSource)
            data = []
            sql.eachRow(request) {
                data << it[0]
            }
            sql.close()
            if(data.size() > 0) new Sql(dataSource).executeUpdate("UPDATE configuration SET reading_role = 'USER' WHERE id IN (" + data.join(",") + ");")


            new Sql(dataSource).executeUpdate("ALTER TABLE configuration DROP COLUMN reading_role_id;")
        }
    }

    void init20180409() {
        log.info "20180409"
        //unused domain
        log.info "drop table"
        new Sql(dataSource).executeUpdate("DROP TABLE image_property;")

        // add ltree column
        log.info "add ltree"
        boolean exists = new Sql(dataSource).rows("SELECT column_name " +
                "FROM information_schema.columns " +
                "WHERE table_name='uploaded_file' and column_name='l_tree';").size() == 1;
        if (!exists) {
            new Sql(dataSource).executeUpdate("CREATE EXTENSION ltree;")
            new Sql(dataSource).executeUpdate("ALTER TABLE uploaded_file ADD COLUMN l_tree ltree;")
        }

        // update ltree
        log.info "update ltree : step 1"
        log.info "record to update : "+UploadedFile.countByParentIsNullAndLTreeIsNull()
        UploadedFile.findAllByParentIsNullAndLTreeIsNull().each {
            it.save()
        }
        def ufs
        log.info "update ltree : step 2"
        log.info "record to update : "+UploadedFile.countByParentIsNotNullAndLTreeIsNull()
        UploadedFile.findAllByParentIsNotNullAndLTreeIsNull().each {
            if(it.lTree == null) {
                ufs = [it]
                def current = it
                while(current.parent != null && current.parent.lTree == null){
                    ufs << current
                    current = current.parent
                }
                ufs = ufs.reverse()
                for(int i = 0;i<ufs.size();i++) {
                    ufs[i].save()
                }
            }
        }

        executorService.execute({

            try {
                log.info "create new uploadedfile"
                // recreate uploadedFile from abstractimage
                // only for converted abstract_image
                ufs = UploadedFile.createCriteria().list {
                    join("image")
                    createAlias("image", "i")
                    neProperty("filename", "i.path")

                    isNotNull("image")
                }
                log.info "record to update : "+ufs.size()

                int i = 0;

                ufs.each {
                    def uf = new UploadedFile()

                    uf.contentType = "image/pyrtiff"
                    uf.image = it.image

                    String filename = it.image.originalFilename
                    int index = filename.lastIndexOf('.')
                    filename = filename.substring(0, index) + "_pyr" + filename.substring(index)

                    uf.originalFilename = filename
                    uf.filename = it.image.path
                    uf.parent = it
                    uf.path = it.path
                    uf.ext = FilenameUtils.getExtension(it.image.path)
                    uf.status = 2 //UploadedFile.DEPLOYED
                    uf.user = it.user
                    uf.storages = StorageAbstractImage.findAllByAbstractImage(it.image).collect { it.storage.id }
                    uf.size = 0L

                    uf.save(failOnError: true)

                    it.image = null
                    it.status = 1 //UploadedFile.CONVERTED
                    it.save()

                    if (i % 100 == 0) log.info("done : " + i + "/" + ufs.size())
                    i++
                }
            } catch (Exception e) {
                log.info "Error during migration. Exit application"
                e.printStackTrace()
                System.exit(1)
            }
        } as Runnable)
    }

    void init20180710() {
        ImageGroup.findAll().each {
            if (it.id.toString() == it.name) {
                def sequence = ImageSequence.findByImageGroup(it)

                List<UploadedFile> files = UploadedFile.findAllByImage(sequence.image.baseImage)
                UploadedFile file = files.size() == 1 ? files[0] : files.find{it.parent!=null}
                while(file.parent) {
                    file = file.parent
                }

                it.setName(file.originalFilename)
                it.save(flush: true)
            }
        }
    }

    void init20180618() {
        boolean exists = new Sql(dataSource).rows("SELECT COLUMN_NAME " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'image_filter' and COLUMN_NAME = 'processing_server_id';").size() == 1
        if (exists) {
            new Sql(dataSource).executeUpdate("UPDATE image_filter SET processing_server_id = NULL;")
            new Sql(dataSource).executeUpdate("ALTER TABLE image_filter DROP COLUMN IF EXISTS processing_server_id;")
        }
        def imagingServer = bootstrapUtilsService.createNewImagingServer()
        ImageFilter.findAll().each {
            it.imagingServer = imagingServer
            it.save(flush: true)
        }

        exists = new Sql(dataSource).rows("SELECT COLUMN_NAME " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = 'processing_server' and COLUMN_NAME = 'url';").size() == 1
        if (exists) {
            new Sql(dataSource).executeUpdate("ALTER TABLE processing_server DROP COLUMN IF EXISTS url;")
            new Sql(dataSource).executeUpdate("DELETE FROM processing_server;")
        }

        new Sql(dataSource).executeUpdate("ALTER TABLE software DROP COLUMN IF EXISTS service_name;")
        new Sql(dataSource).executeUpdate("ALTER TABLE software DROP COLUMN IF EXISTS result_sample;")

        new Sql(dataSource).executeUpdate("UPDATE software SET deprecated = true WHERE deprecated IS NULL;")
        new Sql(dataSource).executeUpdate("UPDATE software_parameter SET server_parameter = false WHERE server_parameter IS NULL;")

        if(SecUser.findByUsername("rabbitmq")) {
            def rabbitmqUser = SecUser.findByUsername("rabbitmq")
            def superAdmin = SecRole.findByAuthority("ROLE_SUPER_ADMIN")
            if(!SecUserSecRole.findBySecUserAndSecRole(rabbitmqUser,superAdmin)) {
                new SecUserSecRole(secUser: rabbitmqUser,secRole: superAdmin).save(flush:true)
            }
        }

        AmqpQueue.findAllByNameLike("queueSoftware%").each {it.delete(flush: true)}

        bootstrapUtilsService.addDefaultProcessingServer()
        bootstrapUtilsService.addDefaultConstraints()
    }

    void init20180301() {
        boolean exists = new Sql(dataSource).rows("SELECT column_name "+
                "FROM information_schema.columns "+
                "WHERE table_name='abstract_image' and column_name='colorspace';").size() == 1;
        if(!exists){
            // add columns
            new Sql(dataSource).executeUpdate("ALTER TABLE abstract_image ADD COLUMN bit_depth integer;")
            new Sql(dataSource).executeUpdate("ALTER TABLE abstract_image ADD COLUMN colorspace varchar(255);")
        }

//        List<AbstractImage> abstractImages = AbstractImage.findAllByDeletedIsNullAndBitDepthIsNull()
//        log.info "${abstractImages.size()} image to populate"
//        abstractImages.eachWithIndex { image, index ->
//            if(index%100==0) {
//                log.info "Populate image properties: ${(index/abstractImages.size())*100}"
//            }
//            imagePropertiesService.populate(image)
//            imagePropertiesService.extractUseful(image)
//        }
    }

    void init20171219() {
        boolean exists = new Sql(dataSource).rows("SELECT column_name "+
                "FROM information_schema.columns "+
                "WHERE table_name='image_grouphdf5' and column_name='progress';").size() == 1;
        if(!exists){
            // add columns
            new Sql(dataSource).executeUpdate("ALTER TABLE image_grouphdf5 ADD COLUMN progress integer DEFAULT 0;")
            new Sql(dataSource).executeUpdate("ALTER TABLE image_grouphdf5 ADD COLUMN status integer DEFAULT 0;")
            new Sql(dataSource).executeUpdate("ALTER TABLE image_grouphdf5 RENAME filenames TO filename;")
        }
    }

    void init20171124() {
        def db = mongo.getDB(noSQLCollectionService.getDatabaseName())
        db.annotationAction.update([:], [$rename:[annotation:'annotationIdent']], false, true)
        db.annotationAction.update([:], [$set:[annotationClassName: 'be.cytomine.ontology.UserAnnotation']], false, true)
        db.annotationAction.update([:], [$unset:[annotation:'']], false, true)
    }

    void init20170714(){
        bootstrapUtilsService.fillProjectConnections();
        bootstrapUtilsService.fillImageConsultations();
        log.info "generate missing storage !"
        for (user in User.findAll()) {
            if (!Storage.findByUser(user)) {
                log.info "generate missing storage for $user"
                SpringSecurityUtils.doWithAuth("admin", {
                    storageService.initUserStorage(user)
                });
            }
        }
    }

    void init20170201() {
        boolean exists = new Sql(dataSource).rows("SELECT column_name " +
                "FROM information_schema.columns " +
                "WHERE table_name='shared_annotation' and column_name='annotation_class_name';").size() == 1;
        if (!exists) {
            // add columns
            new Sql(dataSource).executeUpdate("ALTER TABLE shared_annotation ADD COLUMN annotation_class_name varchar(255);")
            new Sql(dataSource).executeUpdate("ALTER TABLE shared_annotation ADD COLUMN annotation_ident bigint;")

            //update all rows
            new Sql(dataSource).executeUpdate("UPDATE shared_annotation SET annotation_ident = user_annotation_id;")
            new Sql(dataSource).executeUpdate("UPDATE shared_annotation SET annotation_class_name = 'be.cytomine.ontology.UserAnnotation';")

            //add constraints
            new Sql(dataSource).executeUpdate("ALTER TABLE shared_annotation ALTER COLUMN annotation_ident SET NOT NULL;")
            new Sql(dataSource).executeUpdate("ALTER TABLE shared_annotation ALTER COLUMN annotation_class_name SET NOT NULL;")

            //delete
            new Sql(dataSource).executeUpdate("ALTER TABLE shared_annotation DROP COLUMN IF EXISTS user_annotation_id;")
        }
    }

    void init20160901() {

        boolean exists = new Sql(dataSource).rows("SELECT column_name " +
                "FROM information_schema.columns " +
                "WHERE table_name='project' and column_name='mode';").size() == 1;
        if (!exists) {
            new Sql(dataSource).executeUpdate("ALTER TABLE project ADD COLUMN mode varchar(255) NOT NULL DEFAULT 'CLASSIC';")

            String request = "SELECT id FROM project WHERE is_read_only;"
            def sql = new Sql(dataSource)
            def data = []
            sql.eachRow(request) {
                data << it[0]
            }
            sql.close()
            new Sql(dataSource).executeUpdate("UPDATE project SET mode = 'READ_ONLY' WHERE id IN (" + data.join(",") + ");")


            exists = new Sql(dataSource).rows("SELECT column_name " +
                    "FROM information_schema.columns " +
                    "WHERE table_name='project' and column_name='is_read_only';").size() == 1;
            if (exists) {
                log.info "reinit table..."
                new Sql(dataSource).executeUpdate("DROP VIEW user_project;")
                new Sql(dataSource).executeUpdate("DROP VIEW admin_project;")
                new Sql(dataSource).executeUpdate("DROP VIEW creator_project;")
                new Sql(dataSource).executeUpdate("ALTER TABLE project DROP COLUMN is_read_only;")
                tableService.initTable()
            }
        }

        List<Property> properties = Property.findAllByDomainClassNameAndKey(Project.name, "@CUSTOM_UI_PROJECT")
        def configProject;
        properties.each { prop ->
            configProject = JSON.parse(prop.value)
            configProject.each {
                it.value["CONTRIBUTOR_PROJECT"] = it.value["GUEST_PROJECT"]
                it.value.remove("GUEST_PROJECT")
                it.value.remove("USER_PROJECT")
            }
            prop.value = configProject.toString()

            prop.save(true)
        }
    }

    void init20160503() {
        bootstrapUtilsService.convertMimeTypes();
    }

    void init20160324() {
        new Sql(dataSource).executeUpdate("ALTER TABLE uploaded_file DROP COLUMN IF EXISTS mime_type;")
        new Sql(dataSource).executeUpdate("ALTER TABLE uploaded_file DROP COLUMN IF EXISTS converted_filename;")
        new Sql(dataSource).executeUpdate("ALTER TABLE uploaded_file DROP COLUMN IF EXISTS converted_ext;")
        new Sql(dataSource).executeUpdate("ALTER TABLE uploaded_file DROP COLUMN IF EXISTS download_parent_id;")
    }

    void init20160224() {
        new Sql(dataSource).executeUpdate("DELETE FROM attached_file WHERE domain_class_name = 'be.cytomine.image.AbstractImage' AND (filename LIKE '%thumb%' OR filename LIKE '%nested%');")
    }
}