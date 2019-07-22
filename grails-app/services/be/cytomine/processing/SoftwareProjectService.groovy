package be.cytomine.processing

/*
* Copyright (c) 2009-2017. Authors: see NOTICE file.
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

import be.cytomine.Exception.CytomineException
import be.cytomine.command.AddCommand
import be.cytomine.command.Command
import be.cytomine.command.DeleteCommand
import be.cytomine.command.Transaction
import be.cytomine.project.Project
import be.cytomine.security.SecUser
import be.cytomine.security.UserJob
import be.cytomine.utils.ModelService
import be.cytomine.utils.Task
import org.json.simple.JSONObject

import static org.springframework.security.acls.domain.BasePermission.READ

class SoftwareProjectService extends ModelService{

    static transactional = true

    def cytomineService
    def transactionService
    def modelService
    def securityACLService

    def jobRuntimeService
    def jobService
    def currentDomain() {
        return SoftwareProject
    }

    def read(def id) {
        def sp = SoftwareProject.get(id)
        if(sp) {
            securityACLService.check(sp.container(),READ)
        }
        sp
    }

    def list() {
        securityACLService.checkAdmin(cytomineService.currentUser)
        SoftwareProject.list()
    }

    def executeAllWorkflows(Project project){

        securityACLService.checkAdmin(cytomineService.currentUser)

        List<SoftwareProject> listSoftwareProject =SoftwareProject.findAllByProject(project)
        ArrayList<Job> listJobs=new ArrayList<Job>()
        for(int i=0;i<listSoftwareProject.size();i++)
        {
            SoftwareProject sp=SoftwareProject.findById(listSoftwareProject.get(i).id)
            Software softTmp=new Software().findWhere(id:sp.softwareId)
            //we check if it's deprecated or not. If not, we'll create a job
            if(softTmp.deprecated==false)
            {
               log.info("the software isn't deprecated")

                //create a json for the new job
                ArrayList<SoftwareParameter> params=new ArrayList<SoftwareParameter>()
                JSONObject jsonObject = new JSONObject()
                jsonObject.put("software", softTmp.id)
                jsonObject.put("project", project.id)
                jsonObject.put("params", params)

                def jobId=jobService.addJson(jsonObject)
                log.info("new job created! $jobId")

                Job job=new Job().findWhere(id: jobId)
                listJobs.add(i,job)
            }

        }
        jobRuntimeService.executeJobs(listJobs)
        return listJobs
    }

    def list(Project project) {
        securityACLService.check(project.container(),READ)
        SoftwareProject.findAllByProject(project)
    }

    /**
     * Add the new domain with JSON data
     * @param json New domain data
     * @return Response structure (created domain data,..)
     */
    def add(def json) throws CytomineException {
        securityACLService.check(json.project,Project, READ)
        securityACLService.checkisNotReadOnly(json.project,Project)
        SecUser currentUser = cytomineService.getCurrentUser()
        json.user = currentUser.id
        return executeCommand(new AddCommand(user: currentUser),null,json)
    }

    /**
     * Delete this domain
     * @param domain Domain to delete
     * @param transaction Transaction link with this command
     * @param task Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    def delete(SoftwareProject domain, Transaction transaction = null, Task task = null, boolean printMessage = true) {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.check(domain.container(),READ)
        securityACLService.checkisNotReadOnly(domain)
        Command c = new DeleteCommand(user: currentUser,transaction:transaction)
        return executeCommand(c,domain,null)
    }


    def getStringParamsI18n(def domain) {
        return [domain.software?.name, domain.project?.name]
    }
}
