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
import grails.converters.JSON
import grails.gorm.DetachedCriteria
import grails.transaction.Transactional

class ImagePropertiesService implements Serializable {

    def grailsApplication
    def abstractImageService
    def imageServerService

    def keys() {
        def parseString = { x -> x }
        def parseInt = { x -> Integer.parseInt(x) }
        def parseDouble = { x -> Double.parseDouble(x) }
        def parseJSON = { x -> JSON.parse(x) }
        return [
                width        : [name: 'cytomine.width', parser: parseInt],
                height       : [name: 'cytomine.height', parser: parseInt],
                depth        : [name: 'cytomine.depth', parser: parseInt],
                duration     : [name: 'cytomine.duration', parser: parseInt],
                channels     : [name: 'cytomine.channels', parser: parseInt],
                physicalSizeX: [name: 'cytomine.physicalSizeX', parser: parseDouble],
                physicalSizeY: [name: 'cytomine.physicalSizeY', parser: parseDouble],
                physicalSizeZ: [name: 'cytomine.physicalSizeZ', parser: parseDouble],
                fps          : [name: 'cytomine.fps', parser: parseDouble],
                bitPerSample : [name: 'cytomine.bitPerSample', parser: parseInt],
                samplePerPixel: [name: 'cytomine.samplePerPixel', parser: parseInt],
                colorspace   : [name: 'cytomine.colorspace', parser: parseString],
                magnification: [name: 'cytomine.magnification', parser: parseInt],
                resolution   : [name: 'cytomine.resolution', parser: parseDouble],
                channelNames : [name: 'cytomine.channelNames', parser: parseJSON]
        ]
    }

    @Transactional
    def clear(AbstractImage image) {
        def propertyKeys = keys().collect { it.value.name }
        Property.findAllByDomainIdentAndKeyInList(image.id, propertyKeys)?.each {
            it.delete()
        }
    }

    @Transactional
    def populate(AbstractImage image) {
        try {
            def properties = imageServerService.properties(image)
            properties.each {
                String key = it?.key?.toString()?.trim()
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
            log.error(e.printStackTrace())
        }
    }

    @Transactional
    def extractUseful(AbstractImage image, boolean deep = false) {
        def channelNames = [:]
        keys().each { k, v ->
            def property = Property.findByDomainIdentAndKey(image.id, v.name)
            if (property) {
                if (k == "channelNames" && deep) {
                    channelNames = v.parser(property.value)
                }
                else if (k != "channelNames") {
                    image[k] = v.parser(property.value)
                }
            }
            else
                log.info "No property ${v.name} for abstract image $image"
        }
        image.extractedMetadata = new Date()
        image.save(flush: true, failOnError: true)

        if (deep) {
            channelNames.each { channel, name ->
                def  query = new DetachedCriteria(AbstractSlice).build {
                    eq 'image', image
                    eq 'channel', channel as Integer
                }
                query.updateAll(channelName: name as String)
            }
        }
    }

    def regenerate(AbstractImage image, boolean deep = false) {
        clear(image)
        populate(image)
        extractUseful(image, deep)
    }
}
