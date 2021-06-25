package be.cytomine.utils.bootstrap

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

import be.cytomine.security.SecUser
import groovy.sql.Sql
import org.apache.commons.lang.RandomStringUtils

/**
 * Cytomine @ ULG
 * User: stevben
 * Date: 13/03/13
 * Time: 11:30
 */
class BootstrapDataService {

    def grailsApplication
    def bootstrapUtilsService
    def dataSource
    def amqpQueueConfigService

    def initImageFilters() {
        def imagingServer = bootstrapUtilsService.createNewImagingServer()
        def filters = [
                [name: "Binary", method: "binary", imagingServer: imagingServer, available: true],
                [name: "Huang Threshold", method: "huang", imagingServer: imagingServer, available: false],
                [name: "Intermodes Threshold", method: "intermodes", imagingServer: imagingServer, available: false],
                [name: "IsoData Threshold", method: "isodata", imagingServer: imagingServer, available: true],
                [name: "Li Threshold", method: "li", imagingServer: imagingServer, available: false],
                [name: "Max Entropy Threshold", method: "maxentropy", imagingServer: imagingServer, available: false],
                [name: "Mean Threshold", method: "mean", imagingServer: imagingServer, available: true],
                [name: "Minimum Threshold", method: "minimum", imagingServer: imagingServer, available: true],
                [name: "MinError(I) Threshold", method: "minerror", imagingServer: imagingServer, available: false],
                [name: "Moments Threshold", method: "moments", imagingServer: imagingServer, available: false],
                [name: "Otsu Threshold", method: "otsu", imagingServer: imagingServer, available: true],
                [name: "Renyi Entropy Threshold", method: "renyientropy", imagingServer: imagingServer, available: false],
                [name: "Shanbhag Threshold", method: "shanbhag", imagingServer: imagingServer, available: false],
                [name: "Triangle Threshold", method: "triangle", imagingServer: imagingServer, available: false],
                [name: "Yen Threshold", method: "yen", imagingServer: imagingServer, available: true],
                [name: "Percentile Threshold", method: "percentile", imagingServer: imagingServer, available: false],
                [name: "H&E Haematoxylin", method: "he-haematoxylin", imagingServer: imagingServer, available: true],
                [name: "H&E Eosin", method: "he-eosin", imagingServer: imagingServer, available: true],
                [name: "HDAB Haematoxylin", method: "hdab-haematoxylin", imagingServer: imagingServer, available: true],
                [name: "HDAB DAB", method: "hdab-dab", imagingServer: imagingServer, available: true],
                [name: "Haematoxylin", method: "haematoxylin", imagingServer: imagingServer, available: false], //To be removed: does not exist
                [name: "Eosin", method: "eosin", imagingServer: imagingServer, available: false], //To be removed: does not exist
                [name: "Red (RGB)", method: "r_rgb", imagingServer: imagingServer, available: true],
                [name: "Green (RGB)", method: "g_rgb", imagingServer: imagingServer, available: true],
                [name: "Blue (RGB)", method: "b_rgb", imagingServer: imagingServer, available: true],
                [name: "Cyan (CMY)", method: "c_cmy", imagingServer: imagingServer, available: true],
                [name: "Magenta (CMY)", method: "m_cmy", imagingServer: imagingServer, available: true],
                [name: "Yellow (CMY)", method: "y_cmy", imagingServer: imagingServer, available: true],
        ]
        bootstrapUtilsService.createFilters(filters)
    }

    def initData() {

        recreateTableFromNotDomainClass()
        amqpQueueConfigService.initAmqpQueueConfigDefaultValues()

        initImageFilters()

        def nativelySupportedMimes = [
                [extension : 'tif', mimeType : 'image/pyrtiff'],
                [extension : 'jp2', mimeType : 'image/jp2'],
                [extension : 'ndpi', mimeType : 'openslide/ndpi'],
                [extension : 'mrxs', mimeType : 'openslide/mrxs'],
                [extension : 'vms', mimeType : 'openslide/vms'],
                [extension : 'svs', mimeType : 'openslide/svs'],
                [extension : 'scn', mimeType : 'openslide/scn'],
                [extension : 'bif', mimeType : 'openslide/bif'],
                [extension : 'tif', mimeType : 'openslide/ventana'],
                [extension : 'tif', mimeType : 'philips/tif']
        ]
        bootstrapUtilsService.createMimes(nativelySupportedMimes)


        def usersSamples = [
                [username : 'ImageServer1', firstname : 'Image', lastname : 'Server', email : grailsApplication.config.grails.admin.email, group : [[name : "Cytomine"]], password : RandomStringUtils.random(32,  (('A'..'Z') + ('0'..'0')).join().toCharArray()), color : "#FF0000", roles : ["ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"]],
                [username : 'superadmin', firstname : 'Super', lastname : 'Admin', email : grailsApplication.config.grails.admin.email, group : [[name : "Cytomine"]], password : grailsApplication.config.grails.adminPassword, color : "#FF0000", roles : ["ROLE_USER", "ROLE_ADMIN","ROLE_SUPER_ADMIN"]],
                [username : 'admin', firstname : 'Just an', lastname : 'Admin', email : grailsApplication.config.grails.admin.email, group : [[name : "Cytomine"]], password : grailsApplication.config.grails.adminPassword, color : "#FF0000", roles : ["ROLE_USER", "ROLE_ADMIN"]],
                [username : 'rabbitmq', firstname : 'rabbitmq', lastname : 'user', email : grailsApplication.config.grails.admin.email, group : [[name : "Cytomine"]], password : RandomStringUtils.random(32,  (('A'..'Z') + ('0'..'0')).join().toCharArray()), color : "#FF0000", roles : ["ROLE_USER", "ROLE_SUPER_ADMIN"]],
                [username : 'monitoring', firstname : 'Monitoring', lastname : 'Monitoring', email : grailsApplication.config.grails.admin.email, group : [[name : "Cytomine"]], password : RandomStringUtils.random(32,  (('A'..'Z') + ('0'..'0')).join().toCharArray()), color : "#FF0000", roles : ["ROLE_USER","ROLE_SUPER_ADMIN"]]
        ]

        bootstrapUtilsService.createUsers(usersSamples)
        bootstrapUtilsService.createRelation()
        bootstrapUtilsService.createConfigurations(false)

        SecUser admin = SecUser.findByUsername("admin")
        if(!grailsApplication.config.grails.adminPrivateKey) {
            throw new IllegalArgumentException("adminPrivateKey must be set!")
        }
        if(!grailsApplication.config.grails.adminPublicKey) {
            throw new IllegalArgumentException("adminPublicKey must be set!")
        }
        admin.setPrivateKey((String) grailsApplication.config.grails.adminPrivateKey)
        admin.setPublicKey((String) grailsApplication.config.grails.adminPublicKey)
        admin.save(flush : true)

        SecUser superAdmin = SecUser.findByUsername("superadmin")
        if(!grailsApplication.config.grails.superAdminPrivateKey) {
            throw new IllegalArgumentException("superAdminPrivateKey must be set!")
        }
        if(!grailsApplication.config.grails.superAdminPublicKey) {
            throw new IllegalArgumentException("superAdminPublicKey must be set!")
        }
        superAdmin.setPrivateKey((String) grailsApplication.config.grails.superAdminPrivateKey)
        superAdmin.setPublicKey((String) grailsApplication.config.grails.superAdminPublicKey)
        superAdmin.save(flush : true)

        SecUser rabbitMQUser = SecUser.findByUsername("rabbitmq")
        if(!grailsApplication.config.grails.rabbitMQPrivateKey) {
            throw new IllegalArgumentException("rabbitMQPrivateKey must be set!")
        }
        if(!grailsApplication.config.grails.rabbitMQPublicKey) {
            throw new IllegalArgumentException("rabbitMQPublicKey must be set!")
        }
        rabbitMQUser.setPrivateKey(grailsApplication.config.grails.rabbitMQPrivateKey)
        rabbitMQUser.setPublicKey(grailsApplication.config.grails.rabbitMQPublicKey)
        rabbitMQUser.save(flush : true)

        bootstrapUtilsService.addDefaultProcessingServer()
        bootstrapUtilsService.addDefaultConstraints()
    }

    public void recreateTableFromNotDomainClass() {
        new Sql(dataSource).executeUpdate("DROP TABLE IF EXISTS  task_comment")
        new Sql(dataSource).executeUpdate("DROP TABLE IF EXISTS  task")

        new Sql(dataSource).executeUpdate("CREATE TABLE task (id bigint,progress bigint,project_id bigint,user_id bigint,print_in_activity boolean)")
        new Sql(dataSource).executeUpdate("CREATE TABLE task_comment (task_id bigint,comment character varying(255),timestamp bigint)")
    }

}
