package be.cytomine.middleware

import be.cytomine.AnnotationDomain
import be.cytomine.Exception.InvalidRequestException
import be.cytomine.Exception.NotModifiedException
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
import be.cytomine.utils.ImageResponse
import be.cytomine.utils.ModelService
import be.cytomine.utils.StringUtils
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.WKTReader
import grails.converters.JSON
import grails.util.Holders
import groovy.json.JsonOutput
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseDecorator
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

    def formats(ImageServer is) {
        def response = makeRequest("/formats", is.internalUrl, [:],"json", "GET")
        return response?.items?.collect { it -> StringUtils.keysToCamelCase(it) }
    }

    def downloadUri(UploadedFile uploadedFile) {
        if (uploadedFile.isVirtual()) {
            throw new InvalidRequestException("Uploaded file is virtual, it has no valid path.")
        }
        // It gets the file specified in the uri.
        def uri = "/file/${uploadedFile.path}/export"
        makeGetUrl(uri, uploadedFile.imageServer.url, [:])
    }

    def downloadUri(AbstractImage image) {
        UploadedFile uf = image.uploadedFile
        if (uf.isVirtual()) {
            throw new InvalidRequestException("Uploaded file is virtual, it has no valid path.")
        }
        // It gets the original image file, (re-)zipped for multi-file formats if needed.
        def uri = "/image/${uf.path}/export"
        makeGetUrl(uri, uf.imageServer.url, [:])
    }

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

    def imageHistogram(ImageInstance image, int nBins = 256) {
        return imageHistogram(image.baseImage, nBins)
    }

    def imageHistogram(AbstractImage image, int nBins = 256) {
        def (server, path) = imsParametersFromAbstractImage(image)
        def uri = "/image/${path}/histogram/per-image"
        def params = [n_bins: nBins]
        def response = makeRequest(uri, server, params, "json", "GET")
        return StringUtils.keysToCamelCase(response)
    }

    def imageHistogramBounds(ImageInstance image) {
        return imageHistogramBounds(image.baseImage)
    }

    def imageHistogramBounds(AbstractImage image) {
        def (server, path) = imsParametersFromAbstractImage(image)
        def uri = "/image/${path}/histogram/per-image/bounds"
        def response = makeRequest(uri, server, [:], "json", "GET")
        return StringUtils.keysToCamelCase(response)
    }

    def channelHistograms(ImageInstance image, int nBins = 256) {
        return channelHistograms(image.baseImage, nBins)
    }

    def channelHistograms(AbstractImage image, int nBins = 256) {
        def (server, path) = imsParametersFromAbstractImage(image)
        def uri = "/image/${path}/histogram/per-channels"
        def params = [n_bins: nBins]
        def response = makeRequest(uri, server, params, "json", "GET")

        return response?.items?.collect { it -> StringUtils.keysToCamelCase(it) }
    }

    def channelHistogramBounds(ImageInstance image) {
        return channelHistogramBounds(image.baseImage)
    }

    def channelHistogramBounds(AbstractImage image) {
        def (server, path) = imsParametersFromAbstractImage(image)
        def uri = "/image/${path}/histogram/per-channels/bounds"
        def response = makeRequest(uri, server, [:], "json", "GET")

        return response?.items?.collect { it -> StringUtils.keysToCamelCase(it) }
    }

    def planeHistograms(SliceInstance slice, int nBins = 256, boolean allChannels = true) {
        return planeHistograms(slice.baseSlice, nBins, allChannels)
    }

    def planeHistograms(AbstractSlice slice, int nBins = 256, boolean allChannels = true) {
        def (server, path) = imsParametersFromAbstractImage(slice.image)
        def uri = "/image/${path}/histogram/per-plane/z/${slice.zStack}/t/${slice.time}"
        def params = [n_bins: nBins]
        if (!allChannels) {
            params << [channels: slice.channel]
        }
        def response = makeRequest(uri, server, params, "json", "GET")
        return response?.items?.collect { it -> StringUtils.keysToCamelCase(it) }
    }

    def planeHistogramBounds(SliceInstance slice, boolean allChannels = true) {
        return planeHistogramBounds(slice.baseSlice, allChannels)
    }

    def planeHistogramBounds(AbstractSlice slice, boolean allChannels = true) {
        def (server, path) = imsParametersFromAbstractImage(slice.image)
        def uri = "/image/${path}/histogram/per-plane/z/${slice.zStack}/t/${slice.time}/bounds"
        def params = [:]
        if (!allChannels) {
            params << [channels: slice.channel]
        }
        def response = makeRequest(uri, server, params, "json", "GET")
        return response?.items?.collect { it -> StringUtils.keysToCamelCase(it) }
    }

    //TODO
    def makeHDF5(def imageId, def companionFileId, def uploadedFileId) {
        def server = hmsInternalUrl()
        def parameters = [:]
        parameters.image= imageId
        parameters.uploadedFile = uploadedFileId
        parameters.companionFile = companionFileId
        parameters.core = UrlApi.serverUrl()
        return JSON.parse(new String(
                makeRequest("/hdf5.json", server, parameters, "json", "POST")
        ))
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
        return JSON.parse(new String(makeRequest("/profile.json", server, parameters, "json")))
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
        return JSON.parse(new String(makeRequest("/profile/projections.json", server, parameters, "json")))
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

        return makeRequest("/profile/${params.projection}-projection.$format", server, parameters, format)
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

    def label(ImageInstance image, def params, String etag = null) {
        label(image.baseImage, params, etag)
    }

    def label(AbstractImage image, def params, String etag = null) {
        def (server, path) = imsParametersFromAbstractImage(image)
        def format = checkFormat(params.format, ['jpg', 'png', 'webp'])
        def parameters = [length: params.maxSize]
        def uri = "/image/${path}/associated/${params.label.toLowerCase()}"

        def headers = [:]
        if (etag) {
            headers << ['If-None-Match': etag]
        }

        return makeRequest(uri, server, parameters, format, null, headers)
    }

    def thumb(ImageInstance image, def params, String etag = null) {
        thumb(image.baseImage, params, etag)
    }

    def thumb(AbstractImage image, def params, String etag = null) {
        def (server, path) = imsParametersFromAbstractImage(image)
        //TODO: fallback to reference slice if n_channels > X ?
        thumb(server, path, params, etag)
    }

    def thumb(SliceInstance slice, def params, String etag = null) {
        thumb(slice.baseSlice, params, etag)
    }

    def thumb(AbstractSlice slice, def params, String etag = null) {
        def (server, path) = imsParametersFromAbstractSlice(slice)
        if (slice.image.channels > 1) {
            params.put('c', slice.channel)
            // Ensure that if the slice is RGB, the 3 intrinsic channels are used
        }
        params.put('z', slice.zStack)
        params.put('t', slice.time)
        thumb(server, path, params, etag)
    }

    def thumb(String server, String path, def params, String etag = null) {
        def format = checkFormat(params.format, ['jpg', 'png', 'webp'])
        def uri = "/image/${path}/thumb"
        def parameters = [
                length: params.maxSize,
                gammas: params.gamma,
                channels: params.c,
                z_slices: params.z,
                timepoints: params.t,
                colormaps: (params.colormap) ? (List) params.colormap.split(',') : null
        ]

        if (params.bits) {
            parameters.bits = (params.bits == "max") ? "AUTO" : params.bits as Integer
            uri = "/image/${path}/resized"
        }

        if (params.inverse) {
            if (parameters.colormaps) {
                parameters.colormaps = parameters.colormaps.collect { invertColormap(it) }
            }
            else {
                parameters.colormaps = "!DEFAULT"
            }
        }
//        parameters.contrast = params.contrast

        def headers = [:]
        if (etag) {
            headers << ['If-None-Match': etag]
        }

        return makeRequest(uri, server, parameters, format, null, headers)
    }

    def crop(ImageInstance image, GrailsParameterMap params, boolean urlOnly = false,
             boolean parametersOnly = false, String etag = null) {
        crop(image.baseImage, params, urlOnly, parametersOnly, etag)
    }

    def crop(AbstractImage image, GrailsParameterMap params, boolean urlOnly = false,
             boolean parametersOnly = false, String etag = null) {
        def (server, path) = imsParametersFromAbstractImage(image)
        crop(server, path, params, urlOnly, parametersOnly, etag)
    }

    def crop(AnnotationDomain annotation, GrailsParameterMap params, boolean urlOnly = false,
             boolean parametersOnly = false, String etag = null) {
        params.geometry = annotation.location
        crop(annotation.slice, params, urlOnly, parametersOnly, etag)
    }

    def crop(SliceInstance slice, GrailsParameterMap params, boolean urlOnly = false,
             boolean parametersOnly = false, String etag = null) {
        crop(slice.baseSlice, params, urlOnly, parametersOnly, etag)
    }

    def crop(AbstractSlice slice, GrailsParameterMap params, boolean urlOnly = false,
             boolean parametersOnly = false, String etag = null) {
        def (server, path) = imsParametersFromAbstractSlice(slice)
        if (slice.image.channels > 1) {
            params.put('c', slice.channel)
            // Ensure that if the slice is RGB, the 3 intrinsic channels are used
        }
        params.put('z', slice.zStack)
        params.put('t', slice.time)
        crop(server, path, params, urlOnly, parametersOnly, etag)
    }

    def crop(String server, String path, GrailsParameterMap params, boolean urlOnly = false,
             boolean parametersOnly = false, String etag = null) {
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
                annotations: [geometry: wkt],
                channels: params.int('c'),
                z_slices: params.int('z'),
                timepoints: params.int('t'),
                colormaps: (params.colormap) ? (List) params.colormap.split(',') : null
        ]

        if (!params.int('maxSize')) {
            // Zoom parameter is in fact normalized level
            parameters.level = params.int('zoom', 0)
        }

        if (params.bits) {
            parameters.bits = (params.bits == "max") ? "AUTO" : params.int('bits')
        }

        def uri
        def format
        switch (checkType(params)) {
            case 'draw':
                parameters.try_square = params.boolean('square')
                parameters.annotations.stroke_color = params.color?.replace("0x", "#")
                parameters.annotations.stroke_width = params.int('thickness')
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
        if (etag) {
            headers << ['If-None-Match': etag]
        }

        if (params.inverse) {
            if (parameters.colormaps) {
                parameters.colormaps = parameters.colormaps.collect { invertColormap(it) }
            }
            else {
                parameters.colormaps = "!DEFAULT"
            }
        }
//        parameters.contrast = params.double('contrast')
//        parameters.jpegQuality = params.int('jpegQuality')

        if (parametersOnly)
            return [server:server, uri:uri, parameters:parameters]
        if (urlOnly)
            return makeGetUrl(uri, server, parameters)
        return makeRequest(uri, server, parameters, format, "POST", headers)
    }

    def window(ImageInstance image, GrailsParameterMap params,
               boolean urlOnly = false, String etag = null) {
        window(image.baseImage, params, urlOnly, etag)
    }

    def window(AbstractImage image, GrailsParameterMap params,
               boolean urlOnly = false, String etag = null) {
        def (server, path) = imsParametersFromAbstractImage(image)
        window(server, path, params, urlOnly, etag)
    }

    def window(SliceInstance slice, GrailsParameterMap params,
               boolean urlOnly = false, String etag = null) {
        window(slice.baseSlice, params, urlOnly, etag)
    }

    def window(AbstractSlice slice, GrailsParameterMap params,
               boolean urlOnly = false, String etag = null) {
        def (server, path) = imsParametersFromAbstractSlice(slice)
        if (slice.image.channels > 1) {
            params.put('c', slice.channel)
            // Ensure that if the slice is RGB, the 3 intrinsic channels are used
        }
        params.put('z', slice.zStack)
        params.put('t', slice.time)
        window(server, path, params, urlOnly, etag)
    }

    def window(String server, String path, GrailsParameterMap params,
               boolean urlOnly = false, String etag = null) {
        log.debug params
        def parameters = [
                region: [
                        left: params.int('x', 0),
                        top: params.int('y', 0),
                        width: params.int('w'),
                        height: params.int('h')
                ],
                length: params.int('maxSize'),
                gammas: params.double('gamma'),
                channels: params.int('c'),
                z_slices: params.int('z'),
                timepoints: params.int('t'),
                colormaps: (params.colormap) ? (List) params.colormap.split(',') : null
        ]

        if (!params.int('maxSize')) {
            // Cytomine API window zoom parameter is in fact normalized level in PIMS
            parameters.level = params.int('zoom', 0)
        }

        if (params.bits) {
            parameters.bits = (params.bits == "max") ? "AUTO" : params.int('bits')
        }

        if (params.inverse) {
            if (parameters.colormaps) {
                parameters.colormaps = parameters.colormaps.collect { invertColormap(it) }
            }
            else {
                parameters.colormaps = "!DEFAULT"
            }
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

            def annotationStyle = [:]
            switch (annotationType) {
                case 'draw':
                    annotationStyle.mode = "DRAWING"
                    break
                case 'mask':
                    annotationStyle.mode = "MASK"
                    break
                case 'alphaMask':
                case 'alphamask':
                    annotationStyle.mode = "CROP"
                    annotationStyle.background_transparency = params.int('alpha', 100)
                    format = checkFormat(params.format, ['png', 'webp'])
                    break
                default:
                    annotationStyle.mode = "CROP"
                    annotationStyle.background_transparency = 0
            }
            parameters.annotation_style = annotationStyle
        }

        def headers = ['X-Annotation-Origin': 'LEFT_BOTTOM']
        if (params.boolean('safe')) {
            headers << ['X-Image-Size-Safety': 'SAFE_RESIZE']
        }
        if (etag) {
            headers << ['If-None-Match': etag]
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

    private static def extractPIMSHeaders(HttpResponseDecorator.HeadersDecorator headers) {
        def names = ["Cache-Control", "ETag", "X-Annotation-Origin", "X-Image-Size-Limit"]
        def extractedHeaders = [:]
        names.each { name ->
            def h = headers[name] ?: headers[name.toLowerCase()]
            if (h) {
                extractedHeaders << [(name): h.getValue()]
            }
        }
        return extractedHeaders
    }

    private static def makeRequest(def path, def server, def parameters,
                                      def format, def httpMethod=null, def customHeaders=[:]) {
        def final GET_URL_MAX_LENGTH = 512
        parameters = filterParameters(parameters)
        def url = makeGetUrl(path, server, parameters)
        def responseContentType = formatToContentType(format)

        def okClosure = { resp, stream ->
            if (responseContentType == 'application/json')
                return stream
            return new ImageResponse(IOUtils.toByteArray(stream), extractPIMSHeaders(resp.headers))
        }
        def notModifiedClosure = { resp ->
            throw new NotModifiedException(extractPIMSHeaders(resp.headers))
        }
        def badRequestClosure = { resp, json ->
            log.error(json)
            throw new InvalidRequestException("$url returned a 400 Bad Request")
        }
        def notFoundClosure = { resp ->
            log.error(resp)
            throw new ObjectNotFoundException("$url returned a 404 Not found")
        }
        def internalServerErrorClosure = { resp ->
            log.error(resp)
            throw new ServerException("$url returned a 500 Internal error")
        }

        def http = new HTTPBuilder(server)
        if (responseContentType == "image/webp") {
            // Avoid parser registry to throw a warning for unknown content type
            def parserRegistry = http.getParser()
            parserRegistry.putAt("image/webp", parserRegistry.getDefaultParser())
        }

        if ((url.size() < GET_URL_MAX_LENGTH && httpMethod == null) || httpMethod == "GET") {
            http.request(Method.GET, responseContentType) {
                uri.path = path
                uri.query = parameters
                requestContentType = ContentType.JSON
                headers = customHeaders

                response.'200' = okClosure
                response.'304' = notModifiedClosure
                response.'400' = badRequestClosure
                response.'404' = notFoundClosure
                response.'422' = badRequestClosure
                response.'500' = internalServerErrorClosure
            }
        }
        else {
            http.request(Method.POST, responseContentType) {
                uri.path = path
                requestContentType = ContentType.JSON
                body = JsonOutput.toJson(parameters)
                headers = customHeaders

                response.'200' = okClosure
                response.'304' = notModifiedClosure
                response.'400' = badRequestClosure
                response.'404' = notFoundClosure
                response.'422' = badRequestClosure
                response.'500' = internalServerErrorClosure
            }
        }
    }

    private static def makeGetUrl(def uri, def server, def parameters) {
        parameters = filterParameters(parameters)
        String query = parameters.collect { key, value ->
            if (value instanceof List)
                value = value.join(',')

            if (value instanceof Geometry)
                value = value.toText()

            if (value instanceof String)
                value = URLEncoder.encode(value, "UTF-8")
            "$key=$value"
        }.join("&")

        return "$server$uri?$query"
    }

    private static String checkFormat(String format, ArrayList<String> accepted) {
        if (!accepted)
            accepted = ['jpg']

        return (!accepted.contains(format)) ? accepted[0] : format
    }

    private static String formatToContentType(String format) {
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

    String checkType(GrailsParameterMap params) {
        if (params.draw || params.type == 'draw')
            return 'draw'
        else if (params.mask || params.type == 'mask')
            return 'mask'
        else if (params.alphaMask || params.alphamask ||
                params.type?.toLowerCase() == 'alphamask')
            return 'alphaMask'
        else
            return 'crop'
    }

    private static String invertColormap(String colormap) {
        if (colormap[0] == '!') {
            return colormap.substring(1)
        }
        return '!' + colormap
    }
}
