package be.cytomine.image.group

import be.cytomine.AnnotationDomain
import be.cytomine.CytomineDomain

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

import be.cytomine.Exception.WrongArgumentException
import be.cytomine.command.AddCommand
import be.cytomine.command.Command
import be.cytomine.command.DeleteCommand
import be.cytomine.command.Transaction
import be.cytomine.image.ImageInstance
import be.cytomine.security.SecUser
import be.cytomine.utils.ModelService
import be.cytomine.utils.Task

import static org.springframework.security.acls.domain.BasePermission.READ

class ImageGroupImageInstanceService extends ModelService {

    static transactional = true

    def cytomineService
    def commandService
    def securityACLService
    def imageGroupService
    def imageInstanceService

    def currentDomain() {
        ImageGroupImageInstance
    }

    def read(ImageGroup group, ImageInstance image) {
        ImageGroupImageInstance igii = ImageGroupImageInstance.findByGroupAndImage(group, image)
        if (igii) {
            securityACLService.check(igii, READ)
            checkDeleted(igii)
        }
        igii
    }

    def list(ImageGroup group) {
        securityACLService.check(group.project,READ)
        ImageGroupImageInstance.findAllByGroupAndDeletedIsNull(group)
    }

    def list(ImageInstance image) {
        securityACLService.check(image.project,READ)
        ImageGroupImageInstance.findAllByImageAndDeletedIsNull(image)
    }

    def add(def json) {
        ImageGroup group = imageGroupService.read(json.group)
        ImageInstance image = imageInstanceService.read(json.image)

        if (group.project != image.project) {
            throw new WrongArgumentException("Group and image are not in the same project!")
        }

        securityACLService.check(group.project, READ)
        securityACLService.checkisNotReadOnly(group.project)
        SecUser currentUser = cytomineService.getCurrentUser()
        Command c = new AddCommand(user: currentUser)
        executeCommand(c, null, json)
    }

    def delete(ImageGroupImageInstance domain, Transaction transaction = null, Task task = null, boolean printMessage = true) {
        securityACLService.check(domain.group.project, READ)
        securityACLService.checkisNotReadOnly(domain.group.project)
        SecUser currentUser = cytomineService.getCurrentUser()
        Command c = new DeleteCommand(user: currentUser, transaction: transaction)
        executeCommand(c, domain, null)
    }

    def getStringParamsI18n(def domain) {
        return [domain.group.name, domain.image.blindInstanceFilename]
    }

    def retrieve(Map json) {
        ImageGroup group = imageGroupService.read(json.group)
        ImageInstance image = imageInstanceService.read(json.image)

        if (group.project != image.project) {
            throw new WrongArgumentException("Group and image are not in the same project!")
        }

        return read(group, image)
    }
}
