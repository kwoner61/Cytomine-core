package be.cytomine.processing

/*
 * Copyright (c) 2009-2018. Authors: see NOTICE file.
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
import be.cytomine.Exception.MiddlewareException
import be.cytomine.command.*
import be.cytomine.middleware.AmqpQueue
import be.cytomine.middleware.MessageBrokerServer
import be.cytomine.security.SecUser
import be.cytomine.utils.ModelService
import be.cytomine.utils.Task
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import grails.util.Holders
import groovy.json.JsonBuilder
import org.springframework.security.acls.domain.BasePermission

import static org.springframework.security.acls.domain.BasePermission.WRITE

class ProcessingServerService extends ModelService {

    static transactional = true

    def transactionService
    def securityACLService
    def amqpQueueService

    @Override
    def currentDomain() {
        return ProcessingServer
    }

    ProcessingServer get(def id) {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkUser(currentUser)
        return ProcessingServer.get(id)
    }

    ProcessingServer read(def id) {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkUser(currentUser)
        return ProcessingServer.read(id)
    }

    def list() {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkUser(currentUser)
        return ProcessingServer.list()
    }

    def add(def json) throws CytomineException {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkAdmin(currentUser)
        return executeCommand(new AddCommand(user: currentUser), null, json)
    }

    def update(ProcessingServer processingServer, def jsonNewData) {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkAdmin(currentUser)
        return executeCommand(new EditCommand(user: currentUser), processingServer, jsonNewData)
    }

    def delete(ProcessingServer domain, Transaction transaction = null, Task task = null, boolean printMessage = true) {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkAdmin(currentUser)
        Command c = new DeleteCommand(user: currentUser, transaction: transaction)
        return executeCommand(c, domain, null)
    }


    def getPublicKeyPathProcessingServer(Long id) throws CytomineException {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkAdmin(currentUser)
        ProcessingServer processingServer = ProcessingServer.findById(id)
        
        String keyPath=Holders.getGrailsApplication().config.grails.serverSshKeysPath
        def keyPathToReturn = """${keyPath}/${processingServer.host}/${processingServer.host}.pub"""
        return keyPathToReturn

    }

    @Override
    def getStringParamsI18n(def domain) {
        return [domain.name]
    }

    @Override
    def afterAdd(Object domain, Object response) {
        aclUtilService.addPermission(domain, cytomineService.currentUser.username, BasePermission.ADMINISTRATION)
        String queueName = amqpQueueService.queuePrefixProcessingServer + ((domain as ProcessingServer).name).capitalize()
        if (!amqpQueueService.checkAmqpQueueDomainExists(queueName)) {
            // Creates the new queue
            String exchangeName = amqpQueueService.exchangePrefixProcessingServer + ((domain as ProcessingServer).name).capitalize()
            String brokerServerURL = (MessageBrokerServer.findByName("MessageBrokerServer")).host
            AmqpQueue amqpQueue = new AmqpQueue(name: queueName, host: brokerServerURL, exchange: exchangeName)
            amqpQueue.save(failOnError: true)

            amqpQueueService.createAmqpQueueDefault(amqpQueue)

            // Associates the processing server to an amqp queue
            (domain as ProcessingServer).amqpQueue = amqpQueue
            (domain as ProcessingServer).save()

            // Sends a message on the communication queue to warn the software router a new queue has been created
            def message = [requestType: "addProcessingServer",
                           name: amqpQueue.name,
                           host: amqpQueue.host,
                           exchange: amqpQueue.exchange,
                           processingServerId: (domain as ProcessingServer).id]

            JsonBuilder jsonBuilder = new JsonBuilder()
            jsonBuilder(message)

            amqpQueueService.publishMessage(AmqpQueue.findByName("queueCommunication"), jsonBuilder.toString())

            //get the path and name for the SSH Keysfiles
            String keyPath=Holders.getGrailsApplication().config.grails.serverSshKeysPath
            keyPath+="/"
            createKPairSSH(keyPath,(domain as ProcessingServer).host)


        }

    }

    def createKPairSSH(String keyPath,String hostname)
    {
        //this function will create a new ssh key pair. It will also check if a directory and the KPair already exist or not
        boolean bool = false
        keyPath+=hostname
        try {
            File f = new File(keyPath)
            if(f.exists() && f.isDirectory())
            {
                //we'll check if the pair key is inside the folder
                String path=keyPath+"/"+hostname
                log.info("Directory $keyPath already exist! test if the files ${path} &  ${path}.pub exist")
                String folder=path+".pub"
                File fpub=new File(folder)
                File fpri=new File(path)
                if(!fpri.exists() || !fpub.exists())
                {
                    log.info("the pair is missing... we'll create a new one")
                    KeyPair kpair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA)
                    kpair.writePrivateKey(path)
                    kpair.writePublicKey(path + ".pub", "public key of $hostname")
                }
            }
            else {
                bool = f.mkdir()
                log.info("Directory $keyPath created? $bool")
                if (bool) {
                    keyPath += "/" + hostname
                    KeyPair kpair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA)
                    kpair.writePrivateKey(keyPath)
                    kpair.writePublicKey(keyPath + ".pub", "public key of $hostname")
                }
            }
        } catch(Exception e) {e.printStackTrace()}
    }

    def afterUpdate(Object domain, Object response) {
        String queueName = amqpQueueService.queuePrefixProcessingServer + domain.name.capitalize()

        MessageBrokerServer messageBrokerServer = MessageBrokerServer.findByName("MessageBrokerServer")
        if (!amqpQueueService.checkRabbitQueueExists(queueName, messageBrokerServer)) {
            throw new MiddlewareException("The amqp queue doesn't exist, the execution is aborded !")
        }

        def message = [requestType: "updateProcessingServer", processingServerId: (domain as ProcessingServer).id]

        JsonBuilder jsonBuilder = new JsonBuilder()
        jsonBuilder(message)

        amqpQueueService.publishMessage(AmqpQueue.findByName(queueName), jsonBuilder.toString())
    }

}
