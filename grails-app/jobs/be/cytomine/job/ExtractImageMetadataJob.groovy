package be.cytomine.job

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

import be.cytomine.image.AbstractImage
import be.cytomine.image.UploadedFile
import be.cytomine.middleware.ImageServer
import be.cytomine.utils.Version
import org.joda.time.DateTime

class ExtractImageMetadataJob {

    def imagePropertiesService

    static triggers = {
        simple name: 'extractImageMetadataJob', startDelay: 10000, repeatInterval: 1000*15
    }

    def execute() {
//        Version v = Version.getLastVersion()
//        if (v?.major >= 2) {
//            Date yesterday = new DateTime().minusDays(1).toDate()
//            Collection<AbstractImage> abstractImages = AbstractImage.createCriteria().list(max: 10) {
//                createAlias("uploadedFile", "uf")
//                and {
//                    ne("uf.contentType", "virtual/stack")
//                    ne("uf.contentType", "application/zip")
//                    ne("uf.contentType", "CZI")
//                    or {
//                        isNull("bitPerSample")
//                        isNull("width")
//                        eq("width", -1)
//                        ne("channels", 1)
//                    }
//                    isNull("deleted")
//                    isNull("extractedMetadata")
//                    lt("created", yesterday) //to avoid conflict with running image conversions
//                }
//                order("created", "desc")
//            }
//
//            abstractImages.each { image ->
//                try {
//                    ImageServer.withNewSession {
//                        UploadedFile.withNewSession {
//                            AbstractImage.withNewSession {
//                                image.attach()
//                                image.uploadedFile.attach()
//                                image.uploadedFile.imageServer.attach()
//
//                                log.info "Regenerate properties for image $image - ${image.originalFilename}"
//                                try {
//                                    imagePropertiesService.regenerate(image,true)
//                                }
//                                catch (Exception e) {
//                                    log.error "Error during metadata extraction for image $image: ${e.printStackTrace()}"
//                                    image.extractedMetadata = new Date()
//                                    image.save(flush: true)
//                                }
//                            }
//                        }
//                    }
//                }
//                catch (Exception e) {
//                    log.error "Error during metadata extraction for image $image: ${e.printStackTrace()}"
//                }
//            }
//        }


    }
}
