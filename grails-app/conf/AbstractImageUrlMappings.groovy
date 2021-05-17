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

/**
 * Cytomine
 * User: stevben
 * Date: 10/10/11
 * Time: 13:52
 */
class AbstractImageUrlMappings {

    static mappings = {
        /* Abstract Image */
        "/api/abstractimage.$format"(controller: "restAbstractImage"){
            action = [GET:"list", POST:"add"]
        }
        "/api/abstractimage/$id.$format"(controller: "restAbstractImage"){
            action = [GET:"show", PUT:"update", DELETE:"delete"]
        }
        "/api/abstractimage/$id/imageservers.$format"(controller: "restAbstractImage"){
            action = [GET:"imageServers"]
        }
        "/api/project/$id/image.$format"(controller: "restAbstractImage"){
            action = [GET:"listByProject"]
        }
        "/api/abstractimage/unused.$format"(controller:"restAbstractImage"){
            action = [GET: "listUnused"]
        }
        "/api/abstractimage/$id/user.$format"(controller:"restAbstractImage"){
            action = [GET:"showUploaderOfImage"]
        }

        "/api/abstractimage/$id/download"(controller: "restAbstractImage"){
            action = [GET:"download"]
        }
        "/api/abstractimage/$id/thumb.$format"(controller: "restAbstractImage"){
            action = [GET:"thumb", POST:"thumb"]
        }
        "/api/abstractimage/$id/preview.$format"(controller: "restAbstractImage"){
            action = [GET:"preview", POST:"preview"]
        }
        "/api/abstractimage/$id/associated.$format"(controller: "restAbstractImage"){
            action = [GET:"associated"]
        }
        "/api/abstractimage/$id/associated/$label.$format"(controller: "restAbstractImage"){
            action = [GET:"label", POST:"label"]
        }
        "/api/abstractimage/$id/crop.$format"(controller: "restAbstractImage"){
            action = [GET:"crop", POST:"crop"]
        }
        "/api/abstractimage/$id/window-$x-$y-$w-$h.$format"(controller: "restAbstractImage"){
            action = [GET:"window", POST:"window"]
        }
//        "/api/abstractimage/$id/window_url-$x-$y-$w-$h.$format"(controller: "restAbstractImage"){
//            action = [GET:"windowUrl", POST:"windowUrl"]
//        }
//        "/api/abstractimage/$id/camera.$format"(controller: "restAbstractImage"){
//            action = [POST:"camera"]
//        }
//        "/api/abstractimage/$id/camera-$x-$y-$w-$h.$format"(controller: "restAbstractImage"){
//            action = [GET:"camera", POST:"camera"]
//        }
//        "/api/abstractimage/$id/camera_url-$x-$y-$w-$h.$format"(controller: "restAbstractImage"){
//            action = [GET:"cameraUrl", POST:"cameraUrl"]
//        }

        "/api/abstractimage/$id/properties/clear.$format"(controller:"restAbstractImage"){
            action = [POST:"clearProperties"]
        }
        "/api/abstractimage/$id/properties/populate.$format"(controller:"restAbstractImage"){
            action = [POST:"populateProperties"]
        }
        "/api/abstractimage/$id/properties/extract.$format"(controller:"restAbstractImage"){
            action = [POST:"extractProperties"]
        }
        "/api/abstractimage/$id/properties/regenerate.$format"(controller:"restAbstractImage"){
            action = [POST:"regenerateProperties"]
        }
        "/api/abstractimage/$id/histogram/extract.$format"(controller: "restAbstractImage"){
            action = [POST:"extractHistogram"]
        }
        "/api/abstractimage/$id/histogram/stats.$format"(controller: "restAbstractImage"){
            action = [GET: "showHistogramStats"]
        }

        // DEPRECATED: use POST /api/abstractimage/$id.$format instead
//        "/api/uploadedfile/$uploadedFile/image.$format"(controller:"restUploadedFile"){
//            action = [POST:"createImage"]
//        }

        "/api/uploadedfile/$id/abstractimage.$format"(controller: "restAbstractImage") {
            action = [GET: "getByUploadedFile"]
        }
    }
}


