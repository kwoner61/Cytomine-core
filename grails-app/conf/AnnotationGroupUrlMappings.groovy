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

class AnnotationGroupUrlMappings {
    static mappings = {

        "/api/annotationgroup.$format"(controller:"restAnnotationGroup"){
            action = [POST:"add"]
        }
        "/api/annotationgroup/$id.$format"(controller:"restAnnotationGroup"){
            action = [GET:"show",PUT:"update", DELETE:"delete"]
        }
        "/api/project/$id/annotationgroup.$format"(controller:"restAnnotationGroup"){
            action = [GET:"listByProject"]
        }
        "/api/imagegroup/$id/annotationgroup.$format"(controller:"restAnnotationGroup"){
            action = [GET:"listByImageGroup"]
        }

        "/api/annotationgroup/$annotationGroup/annotation/$annotation.$format"(controller: "restAnnotationLink"){
            action = [GET: "show", DELETE: "delete"]
        }

        "/api/annotationlink.$format"(controller: "restAnnotationLink"){
            action = [POST: "add"]
        }

        "/api/annotationgroup/$id/annotationlink.$format"(controller: "restAnnotationLink") {
            action = [GET: "listByAnnotationGroup"]
        }

        "/api/annotation/$id/annotationlink.$format"(controller: "restAnnotationLink") {
            action = [GET: "listByAnnotation"]
        }
    }
}
