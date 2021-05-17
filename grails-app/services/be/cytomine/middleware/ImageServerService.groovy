package be.cytomine.middleware

import be.cytomine.AnnotationDomain
import be.cytomine.Exception.InvalidRequestException
import be.cytomine.Exception.ObjectNotFoundException
import be.cytomine.Exception.ServerException
import be.cytomine.api.UrlApi
import be.cytomine.image.AbstractImage
import be.cytomine.image.AbstractSlice
import be.cytomine.image.CompanionFile
import be.cytomine.image.ImageInstance
import be.cytomine.image.SliceInstance
import be.cytomine.image.UploadedFile
import be.cytomine.utils.GeometryUtils
import be.cytomine.utils.ModelService
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.WKTReader
import grails.converters.JSON
import grails.util.Holders
import groovy.json.JsonOutput
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.commons.io.IOUtils
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap

class ImageServerService extends ModelService {
    /* TODO: delete dependent objects - do we want to allow this ?
        - uploadedFile
        - mimeImageServer
     */

    static transactional = true

    def cytomineService
    def securityACLService
    def simplifyGeometryService

    def list() {
        securityACLService.checkGuest(cytomineService.currentUser)
        ImageServer.list()
    }

    def read(def id) {
        securityACLService.checkGuest(cytomineService.currentUser)
        return ImageServer.read(id as Long)
    }

    def currentDomain() {
        ImageServer
    }

    def storageSpace(ImageServer is) {
        return makeRequest("/storage/size.json", is.internalUrl, [:], "GET")
    }

    //TODO
    def downloadUri(UploadedFile uploadedFile) {
        if (!uploadedFile.path) {
            throw new InvalidRequestException("Uploaded file has no valid path.")
        }
        makeGetUrl("/image/download", uploadedFile.imageServer.url,
                [fif: uploadedFile.path, mimeType: uploadedFile.contentType])
    }

    def downloadUri(AbstractImage image) {
        downloadUri(image.uploadedFile)
    }

    //TODO
    def downloadUri(CompanionFile file) {
        downloadUri(file.uploadedFile)
    }


    def properties(AbstractImage image) {
        def (server, path) = imsParametersFromAbstractImage(image)
        def uri = "/image/${path}/info"
        def response = makeRequest(uri, server, [:], "json", "GET")
        return response
    }

    def rawProperties(AbstractImage image) {
        def (server, path) = imsParametersFromAbstractImage(image)
        def uri = "/image/${path}/metadata"
        def response = makeRequest(uri, server, [:], "json", "GET")
        return response?.items
    }

    //TODO
    def sampleHistograms(AbstractSlice slice) {
        def (server, parameters) = imsParametersFromAbstractSlice(slice)
        parameters.samplePerPixel = slice?.image?.samplePerPixel
        parameters.bitPerSample = slice?.image?.bitPerSample
        return JSON.parse(new String(makeRequest("/slice/histogram.json", server, parameters, "GET")))
    }

    //TODO
    def makeHDF5(def imageId, def companionFileId, def uploadedFileId) {
        def server = hmsInternalUrl()
        def parameters = [:]
        parameters.image= imageId
        parameters.uploadedFile = uploadedFileId
        parameters.companionFile = companionFileId
        parameters.core = UrlApi.serverUrl()
        return JSON.parse(new String(makeRequest("/hdf5.json", server, parameters, "POST")))
    }

    //TODO
    def profile(CompanionFile profileCf, AnnotationDomain annotation, def params) {
        profile(profileCf, annotation.location, params)
    }

    //TODO
    def profile(CompanionFile profile, Geometry geometry, def params) {
        def (server, parameters) = hmsParametersFromCompanionFile(profile)
        parameters.location = geometry.toString()
        parameters.minSlice = params.minSlice
        parameters.maxSlice = params.maxSlice
        return JSON.parse(new String(makeRequest("/profile.json", server, parameters)))
    }

    //TODO
    def profileProjections(CompanionFile profile, AnnotationDomain annotation, def params) {
        profileProjections(profile, annotation.location, params)
    }

    //TODO
    def profileProjections(CompanionFile profile, Geometry geometry, def params) {
        def (server, parameters) = hmsParametersFromCompanionFile(profile)
        parameters.location = geometry.toString()
        parameters.minSlice = params.minSlice
        parameters.maxSlice = params.maxSlice
        parameters.axis = params.axis
        parameters.dimension = params.dimension
        return JSON.parse(new String(makeRequest("/profile/projections.json", server, parameters)))
    }

    //TODO
    def profileImageProjection(CompanionFile profile, AnnotationDomain annotation, def params) {
        profileImageProjection(profile, annotation.location, params)
    }

    //TODO
    def profileImageProjection(CompanionFile profile, Geometry geometry, def params) {
        def (server, parameters) = hmsParametersFromCompanionFile(profile)
        def format = checkFormat(params.format, ['jpg', 'png'])
        parameters.location = geometry.toString()
        parameters.minSlice = params.minSlice
        parameters.maxSlice = params.maxSlice

        return makeRequest("/profile/${params.projection}-projection.$format", server, parameters)
    }

    def associated(ImageInstance image) {
        associated(image.baseImage)
    }

    def associated(AbstractImage image) {
        def (server, path) = imsParametersFromAbstractImage(image)
        def uri = "/image/${path}/info/associated"
        def response = makeRequest(uri, server, [:], "json", "GET")
        return response?.items?.collect {
            it.name
        }
    }

    def label(ImageInstance image, def params) {
        label(image.baseImage, params)
    }

    def label(AbstractImage image, def params) {
        def (server, path) = imsParametersFromAbstractImage(image)
        def format = checkFormat(params.format, ['jpg', 'png', 'webp'])
        def parameters = [length: params.maxSize]
        def uri = "/image/${path}/associated/${params.label.toLowerCase()}"
        return makeRequest(uri, server, parameters, format)
    }

    def thumb(ImageInstance image, def params) {
        thumb(image.referenceSlice, params)
    }

    def thumb(SliceInstance slice, def params) {
        thumb(slice.baseSlice, params)
    }

    def thumb(AbstractImage image, def params) {
        thumb(image.referenceSlice, params)
    }

    def thumb(AbstractSlice slice, def params) {
        def (server, path) = imsParametersFromAbstractSlice(slice)
        def format = checkFormat(params.format, ['jpg', 'png', 'webp'])
        def uri = "/image/${path}/thumb"
        def parameters = [
                length: params.maxSize,
                gammas: params.gamma
        ]

        if (params.bits) {
            parameters.bits = (params.bits == "max") ? "AUTO" : params.int('bits')
            uri = "/image/${path}/resized"
        }
//        parameters.colormap = params.colormap
//        parameters.inverse = params.inverse
//        parameters.contrast = params.contrast

        return makeRequest(uri, server, parameters, format)
    }

    def crop(AnnotationDomain annotation, GrailsParameterMap params, def urlOnly = false, def parametersOnly = false) {
        params.geometry = annotation.location
        crop(annotation.slice, params, urlOnly, parametersOnly)
    }

    def crop(ImageInstance image, GrailsParameterMap params, def urlOnly = false, def parametersOnly = false) {
        crop(image.baseImage.referenceSlice, params, urlOnly, parametersOnly)
    }

    def crop(SliceInstance slice, GrailsParameterMap params, def urlOnly = false, def parametersOnly = false) {
        crop(slice.baseSlice, params, urlOnly, parametersOnly)
    }

    def crop(AbstractSlice slice, GrailsParameterMap params, def urlOnly = false, def parametersOnly = false) {
        log.debug params
        def (server, path) = imsParametersFromAbstractSlice(slice)

        def geometry = params.geometry
        if (!geometry && params.location) {
            geometry = new WKTReader().read(params.location as String)
        }

        def wkt = null
        if (params.complete && geometry)
            wkt = simplifyGeometryService.reduceGeometryPrecision(geometry).toText()
        else if (geometry)
            wkt = simplifyGeometryService.simplifyPolygonForCrop(geometry).toText()

        def parameters = [
                length: params.int('maxSize'),
                context_factor: params.double('increaseArea'),
                gammas: params.double('gamma'),
                annotations: [geometry: wkt]
        ]

        if (!params.int('maxSize')) {
            // Zoom parameter is in fact normalized level
            parameters.level = params.int('zoom', 0)
        }

        def uri
        def format
        switch (checkType(params)) {
            case 'draw':
                parameters.try_square = params.boolean('square')
                parameters.annotations.stroke_color = params.color?.replace("0x", "#") ?: "black"
                parameters.annotations.stroke_width = params.int('thickness') ?: 1 // TODO: check scale
                uri = "/image/${path}/annotation/drawing"
                format = checkFormat(params.format, ['jpg', 'png', 'webp'])
                break
            case 'mask':
                parameters.annotations.fill_color = '#fff'
                uri = "/image/${path}/annotation/mask"
                format = checkFormat(params.format, ['jpg', 'png', 'webp'])
                break
            case 'alphaMask':
            case 'alphamask':
                parameters.background_transparency = params.int('alpha', 100)
                uri = "/image/${path}/annotation/crop"
                format = checkFormat(params.format, ['png', 'webp'])
                break
            default:
                parameters.background_transparency = 0
                uri = "/image/${path}/annotation/crop"
                format = checkFormat(params.format, ['jpg', 'png', 'webp'])
        }

        def headers = ['X-Annotation-Origin': 'LEFT_BOTTOM']
        if (params.boolean('safe')) {
            headers << ['X-Image-Size-Safety': 'SAFE_RESIZE']
        }

//        parameters.colormap = params.colormap
//        parameters.inverse = params.boolean('inverse')
//        parameters.contrast = params.double('contrast')
//        parameters.bits = (params.bits == "max") ? (slice.image.bitPerSample ?: 8) : params.int('bits')
//        parameters.jpegQuality = params.int('jpegQuality')

        if (parametersOnly)
            return [server:server, uri:uri, parameters:parameters]
        if (urlOnly)
            return makeGetUrl(uri, server, parameters)
        return makeRequest(uri, server, parameters, format, "POST", headers)
    }

    def window(ImageInstance image, GrailsParameterMap params, def urlOnly = false) {
        window(image.baseImage.referenceSlice, params, urlOnly)
    }

    def window(AbstractImage image, GrailsParameterMap params, def urlOnly = false) {
        window(image.referenceSlice, params, urlOnly)
    }

    def window(SliceInstance slice, GrailsParameterMap params, def urlOnly = false) {
        window(slice.baseSlice, params, urlOnly)
    }

    def window(AbstractSlice slice, GrailsParameterMap params, def urlOnly = false) {
        log.debug params
        def (server, path) = imsParametersFromAbstractSlice(slice)

        def parameters = [
                region: [
                        left: params.int('x', 0),
                        top: params.int('y', 0),
                        width: params.int('w'),
                        height: params.int('h')
                ],
                length: params.int('maxSize'),
                gammas: params.double('gamma'),
        ]

        if (!params.int('maxSize')) {
            // Zoom parameter is in fact normalized level
            parameters.level = params.int('zoom', 0)
        }

        if (params.bits) {
            parameters.bits = (params.bits == "max") ? "AUTO" : params.int('bits')
        }

        def format = checkFormat(params.format, ['jpg', 'png', 'webp'])

        if (params.geometries) {
            def strokeColor = params.color?.replace("0x", "#") ?: "black"
            def strokeWidth = params.int('thickness') ?: 1 // TODO: check scale
            def annotationType = checkType(params)

            parameters.annotations = params.geometries.collect { geometry ->
                def wkt = null
                if (params.complete)
                    wkt = simplifyGeometryService.reduceGeometryPrecision(geometry).toText()
                else
                    wkt = simplifyGeometryService.simplifyPolygonForCrop(geometry).toText()

                def annot = [geometry: wkt]

                switch (annotationType) {
                    case 'draw':
                        annot.stroke_color = strokeColor
                        annot.stroke_width = strokeWidth
                        break
                    case 'mask':
                        annot.fill_color = '#fff'
                        break
                }

                return annot
            }

            parameters.annotation_style = [:]
            switch (annotationType) {
                case 'draw':
                    parameters.annotation_style.mode = "DRAWING"
                    break
                case 'mask':
                    parameters.annotation_style.mode = "MASK"
                    break
                case 'alphaMask':
                case 'alphamask':
                    parameters.annotation_style.mode = "CROP"
                    parameters.annotation_style.background_transparency = params.int('alpha', 100)
                    format = checkFormat(params.format, ['png', 'webp'])
                    break
                default:
                    parameters.annotation_style.mode = "CROP"
                    parameters.annotation_style.background_transparency = 0
            }
        }

        def headers = ['X-Annotation-Origin': 'LEFT_BOTTOM']
        if (params.boolean('safe')) {
            headers << ['X-Image-Size-Safety': 'SAFE_RESIZE']
        }

        def uri = "/image/${path}/window"
        if (urlOnly)
            return makeGetUrl(uri, server, parameters)
        return makeRequest(uri, server, parameters, format, "POST", headers)

//        def boundaries = [:]
//        boundaries.topLeftX = Math.max((int) params.int('x'), 0)
//        boundaries.topLeftY = Math.max((int) params.int('y'), 0)
//        boundaries.width = params.int('w')
//        boundaries.height = params.int('h')
//
//        def withExterior = params.boolean('withExterior', false)
//        if (!withExterior) {
//            // Do not take part outside of the real image
//            if(slice.image.width && (boundaries.width + boundaries.topLeftX) > slice.image.width) {
//                boundaries.width = slice.image.width - boundaries.topLeftX
//            }
//            if(slice.image.height && (boundaries.height + boundaries.topLeftY) > slice.image.height) {
//                boundaries.height = slice.image.height - boundaries.topLeftY
//            }
//        }
//
//        boundaries.topLeftY = Math.max((int) (slice.image.height - boundaries.topLeftY), 0)
//        params.boundaries = boundaries
//        crop(slice, params, urlOnly)

        //        parameters.drawScaleBar = params.boolean('drawScaleBar')
//        parameters.resolution = (params.boolean('drawScaleBar')) ? params.double('resolution') : null
//        parameters.magnification = (params.boolean('drawScaleBar')) ? params.double('magnification') : null
    }

    private static def imsParametersFromAbstractImage(AbstractImage image) {
        if (!image.path) {
            throw new InvalidRequestException("Abstract image has no valid path.")
        }

        def server = image.getImageServerInternalUrl()
        return [server, image.path]
    }

    private static def imsParametersFromAbstractSlice(AbstractSlice slice) {
        if (!slice.path) {
            throw new InvalidRequestException("Abstract slice has no valid path.")
        }

        def server = slice.getImageServerInternalUrl()
        return [server, slice.path]
    }

    //TODO
    private static def hmsInternalUrl() {
        def url = Holders.config.grails.hyperspectralServerURL
        return (Holders.config.grails.useHTTPInternally) ? url.replace("https", "http") : url;
    }

    //TODO
    private static def hmsParametersFromCompanionFile(CompanionFile cf) {
        if (!cf.path) {
            throw new InvalidRequestException("Companion file has no valid path.")
        }

        def server = hmsInternalUrl()
        def parameters = [fif: cf.path]
        return [server, parameters]
    }

    private static def filterParameters(parameters) {
        parameters.findAll {
            it.value != null && it.value != ""
        }
    }

    private static def makeRequest(def path, def server, def parameters,
                                      def format, def httpMethod=null, def customHeaders=[:]) {
        def final GET_URL_MAX_LENGTH = 512
        parameters = filterParameters(parameters)
        def url = makeGetUrl(path, server, parameters)
        def responseContentType = formatToContentType(format)

        def http = new HTTPBuilder(server)
        if ((url.size() < GET_URL_MAX_LENGTH && httpMethod == null) || httpMethod == "GET") {
            http.request(Method.GET, responseContentType) {
                uri.path = path
                uri.query = parameters
                requestContentType = ContentType.JSON
                headers = customHeaders

                response.success = { resp, stream ->
                    if (responseContentType == 'application/json')
                        return stream
                    return IOUtils.toByteArray(stream)
                }

                response.'400' = { resp, json ->
                    log.error(json)
                    throw new InvalidRequestException("$url returned a 400 Bad Request")
                }

                response.'404' = { resp ->
                    log.error(resp)
                    throw new ObjectNotFoundException("$url returned a 404 Not found")
                }

                response.'500' = { resp ->
                    log.error(resp)
                    throw new ServerException("$url returned a 500 Internal error")
                }
            }
        }
        else {
            http.request(Method.POST, responseContentType) {
                uri.path = path
                requestContentType = ContentType.JSON
                body = JsonOutput.toJson(parameters)
                headers = customHeaders

                response.success = { resp, stream ->
                    if (responseContentType == 'application/json')
                        return stream
                    return IOUtils.toByteArray(stream)
                }

                response.'400' = { resp ->
                    log.error(resp)
                    throw new InvalidRequestException("$url (with parameters $parameters) returned a 400 Bad Request")
                }

                response.'404' = { resp ->
                    log.error(resp)
                    throw new ObjectNotFoundException("$url (with parameters $parameters) returned a 404 Not found")
                }

                response.'500' = { resp ->
                    log.error(resp)
                    throw new ServerException("$url (with parameters $parameters) returned a 500 Internal error")
                }
            }
        }
    }

    private static def makeGetUrl(def uri, def server, def parameters) {
        parameters = filterParameters(parameters)
        String query = parameters.collect { key, value ->
            if (value instanceof Geometry)
                value = value.toText()

            if (value instanceof String)
                value = URLEncoder.encode(value, "UTF-8")
            "$key=$value"
        }.join("&")

        return "$server$uri?$query"
    }

    private static def checkFormat(def format, def accepted) {
        if (!accepted)
            accepted = ['jpg']

        return (!accepted.contains(format)) ? accepted[0] : format
    }

    private static def formatToContentType(def format) {
        switch (format) {
            case 'png':
                return 'image/png'
            case 'webp':
                return 'image/webp'
            case 'jpg':
                return 'image/jpeg'
            default:
                return 'application/json'
        }
    }

    def checkType(def params) {
        if (params.draw || params.type == 'draw')
            return 'draw'
        else if (params.mask || params.type == 'mask')
            return 'mask'
        else if (params.alphaMask || params.alphamask || params.type?.toLowerCase() == 'alphamask')
            return 'alphaMask'
        else
            return 'crop'
    }
}
