package be.cytomine.image

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

import be.cytomine.meta.Property
import grails.gorm.DetachedCriteria
import grails.transaction.Transactional

class ImagePropertiesService implements Serializable {

    def grailsApplication
    def abstractImageService
    def imageServerService

    @Transactional
    def clear(AbstractImage image) {
        Property.where {
            domainClassName == image.class.name
            domainIdent == image.id
        }.deleteAll()
    }

    @Transactional
    def populate(AbstractImage image) {
        try {
            def properties = imageServerService.rawProperties(image)
            properties.each {
                String namespace = (it?.namespace) ? it?.namespace + "." : ""
                String key = namespace + it?.key?.toString()?.trim()
                String value = it?.value?.toString()?.trim()
                if (key && value) {
                    key = key.replaceAll("\u0000", "")
                    value = value.replaceAll("\u0000", "")
                    def property = Property.findByDomainIdentAndKey(image.id, key)
                    if (!property) {
                        try {
                            log.debug("New property: $key => $value for abstract image $image")
                            property = new Property(key: key, value: value, domainIdent: image.id, domainClassName: image.class.name)
                            property.save(failOnError: true)
                        } catch(Exception e) {
                            log.error(e)
                        }
                    }
                }
            }
        } catch(Exception e) {
            log.error(e)
            e.printStackTrace()
        }
    }

    @Transactional
    def extractUseful(AbstractImage image, boolean deep = false) {
        try {
            def properties = imageServerService.properties(image)
            image.width = properties?.image?.width
            image.height = properties?.image?.height
            image.depth = properties?.image?.depth
            image.duration = properties?.image?.duration
            image.channels = properties?.image?.n_intrinsic_channels
            image.extrinsicChannels = properties?.image?.n_channels
            image.physicalSizeX = properties?.image?.physical_size_x
            image.physicalSizeY = properties?.image?.physical_size_y
            image.physicalSizeZ = properties?.image?.physical_size_z
            image.fps = properties?.image?.frame_rate
            image.magnification = properties?.instrument?.objective?.nominal_magnification

            def pixelType = [
                    "uint8": 8,
                    "int8": 8,
                    "uint16": 16,
                    "int16": 16
            ]
            image.bitPerSample = pixelType.getOrDefault(properties?.image?.pixel_type, 8)
            image.samplePerPixel = properties?.image?.n_channels / properties?.image?.n_intrinsic_channels


            image.resolution = properties?.image?.physical_size_x // TODO: remove
            image.tileSize = 256 // [PIMS] At this stage, we only support normalized-tiles.

            image.extractedMetadata = new Date()
            image.save(flush: true, failOnError: true)

            if (deep) {
                properties?.channels?.each { channel ->
                    def  query = new DetachedCriteria(AbstractSlice).build {
                        eq 'image', image
                        eq 'channel', channel.index as Integer
                    }
                    query.updateAll(
                            channelName: channel.suggested_name as String,
                            channelColor: channel.color as String
                    )
                }
            }
        } catch(Exception e) {
            log.error(e)
            e.printStackTrace()
        }
    }

    def regenerate(AbstractImage image, boolean deep = false) {
        clear(image)
        populate(image)
        extractUseful(image, deep)
    }
}
