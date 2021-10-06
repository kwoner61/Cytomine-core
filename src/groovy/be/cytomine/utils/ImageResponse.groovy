package be.cytomine.utils

/*
* Copyright (c) 2009-2021. Authors: see NOTICE file.
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


class ImageResponse {
    byte[] content
    Map headers

    ImageResponse(byte[] content, def headers) {
        this.content = content
        this.headers = headers
    }

    ImageResponse(byte[] content) {
        ImageResponse(content, [:])
    }

    void setCacheControlMaxAge(int timeToLive) {
        String cacheControl = headers.get("Cache-Control")
        if (!cacheControl) {
            headers << ["Cache-Control": "max-age=${timeToLive}"]
        }
        else {
            def parts = cacheControl.split(",")
            String header = parts.collect {
                if (it.startsWith("max-age")) {
                    return "max-age=${timeToLive}"
                }
                return it
            }.join(',')
            headers."Cache-Control" = header
        }
    }

}
