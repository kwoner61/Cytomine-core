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
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.commons.io.IOUtils

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
        return JSON.parse(new String(makeRequest("/storage/size.json", is.internalUrl, [:], "GET")))
    }

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

    def downloadUri(CompanionFile file) {
        downloadUri(file.uploadedFile)
    }

    def properties(AbstractImage image) {
        def (server, parameters) = imsParametersFromAbstractImage(image)
        return JSON.parse(new String(makeRequest("/image/properties.json", server, parameters, "GET")))
    }

    def sampleHistograms(AbstractSlice slice) {
        def (server, parameters) = imsParametersFromAbstractSlice(slice)
        parameters.samplePerPixel = slice?.image?.samplePerPixel
        parameters.bitPerSample = slice?.image?.bitPerSample
        return JSON.parse(new String(makeRequest("/slice/histogram.json", server, parameters, "GET")))
    }

    def makeHDF5(def imageId, def companionFileId, def uploadedFileId) {
        def server = hmsInternalUrl()
        def parameters = [:]
        parameters.image= imageId
        parameters.uploadedFile = uploadedFileId
        parameters.companionFile = companionFileId
        parameters.core = UrlApi.serverUrl()
        return JSON.parse(new String(makeRequest("/hdf5.json", server, parameters, "POST")))
    }

    def profile(CompanionFile profileCf, AnnotationDomain annotation, def params) {
        profile(profileCf, annotation.location, params)
    }

    def profile(CompanionFile profile, Geometry geometry, def params) {
        def (server, parameters) = hmsParametersFromCompanionFile(profile)
        parameters.location = geometry.toString()
        parameters.minSlice = params.minSlice
        parameters.maxSlice = params.maxSlice
        return JSON.parse(new String(makeRequest("/profile.json", server, parameters)))
    }

    def profileProjections(CompanionFile profile, AnnotationDomain annotation, def params) {
        profileProjections(profile, annotation.location, params)
    }

    def profileProjections(CompanionFile profile, Geometry geometry, def params) {
        def (server, parameters) = hmsParametersFromCompanionFile(profile)
        parameters.location = geometry.toString()
        parameters.minSlice = params.minSlice
        parameters.maxSlice = params.maxSlice
        parameters.axis = params.axis
        parameters.dimension = params.dimension
        return JSON.parse(new String(makeRequest("/profile/projections.json", server, parameters)))
    }

    def profileImageProjection(CompanionFile profile, AnnotationDomain annotation, def params) {
        profileImageProjection(profile, annotation.location, params)
    }

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
        def (server, parameters) = imsParametersFromAbstractImage(image)
        return JSON.parse(new String(makeRequest("/image/associated.json", server, parameters, "GET")))
    }

    def label(ImageInstance image, def params) {
        label(image.baseImage, params)
    }

    def label(AbstractImage image, def params) {
        def (server, parameters) = imsParametersFromAbstractImage(image)
        def format = checkFormat(params.format, ['jpg', 'png'])
        parameters.maxSize = params.maxSize
        parameters.label = params.label
        return makeRequest("/image/nested.$format", server, parameters)
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
        def (server, parameters) = imsParametersFromAbstractSlice(slice)
        def format = checkFormat(params.format, ['jpg', 'png'])
        parameters.maxSize = params.maxSize
        parameters.colormap = params.colormap
        parameters.inverse = params.inverse
        parameters.contrast = params.contrast
        parameters.gamma = params.gamma
        parameters.bits = (params.bits == "max") ? (slice.image.bitPerSample ?: 8) : params.bits

//        AttachedFile attachedFile = AttachedFile.findByDomainIdentAndFilename(abstractImage.id, url)
//        if (attachedFile) {
//            return ImageIO.read(new ByteArrayInputStream(attachedFile.getData()))
//        } else {
//            String imageServerURL = abstractImage.getRandomImageServerURL()
//            byte[] imageData = new URL("$imageServerURL"+url).getBytes()
//            BufferedImage bufferedImage =  ImageIO.read(new ByteArrayInputStream(imageData))
//            attachedFileService.add(url, imageData, abstractImage.id, AbstractImage.class.getName())
//            return bufferedImage
//        }

        return makeRequest("/slice/thumb.$format", server, parameters)
    }

    def crop(AnnotationDomain annotation, def params, def urlOnly = false, def parametersOnly = false) {
        params.geometry = annotation.location
        crop(annotation.slice, params, urlOnly, parametersOnly)
    }

    def crop(ImageInstance image, def params, def urlOnly = false, def parametersOnly = false) {
        crop(image.baseImage.referenceSlice, params, urlOnly, parametersOnly)
    }

    def crop(SliceInstance slice, def params, def urlOnly = false, def parametersOnly = false) {
        crop(slice.baseSlice, params, urlOnly, parametersOnly)
    }

    def crop(AbstractSlice slice, def params, def urlOnly = false, def parametersOnly = false) {
        log.debug params
        def (server, parameters) = imsParametersFromAbstractSlice(slice)

        def geometry = params.geometry
        if (!geometry && params.location) {
            geometry = new WKTReader().read(params.location as String)
        }

        // In the window service, boundaries are already set and do not correspond to geometry/location boundaries
        def boundaries = params.boundaries
        if (!boundaries && geometry) {
            boundaries = GeometryUtils.getGeometryBoundaries(geometry)
        }
        parameters.topLeftX = boundaries.topLeftX
        parameters.topLeftY = boundaries.topLeftY
        parameters.width = boundaries.width
        parameters.height = boundaries.height

        if (params.complete && geometry)
            parameters.location = simplifyGeometryService.reduceGeometryPrecision(geometry).toText()
        else if (geometry)
            parameters.location = simplifyGeometryService.simplifyPolygonForCrop(geometry)

        parameters.imageWidth = slice.image.width
        parameters.imageHeight = slice.image.height
        parameters.maxSize = params.int('maxSize')
        parameters.zoom = (!params.int('maxSize')) ? params.int('zoom') : null
        parameters.increaseArea = params.double('increaseArea')
        parameters.safe = params.boolean('safe')
        parameters.square = params.boolean('square')

//        if(location instanceof com.vividsolutions.jts.geom.Point && !params.point.equals("false")) {
//            boundaries.point = true
//        }

        parameters.type = checkType(params, ['crop', 'draw', 'mask', 'alphaMask'])
        def format
        if (parameters.type == 'alphaMask') {
            format = checkFormat(params.format, ['png'])
        }
        else {
            format = checkFormat(params.format, ['jpg', 'png', 'tiff'])
        }

        parameters.drawScaleBar = params.boolean('drawScaleBar')
        parameters.resolution = (params.boolean('drawScaleBar')) ? params.double('resolution') : null
        parameters.magnification = (params.boolean('drawScaleBar')) ? params.double('magnification') : null

        parameters.colormap = params.colormap
        parameters.inverse = params.boolean('inverse')
        parameters.contrast = params.double('contrast')
        parameters.gamma = params.double('gamma')
        parameters.bits = (params.bits == "max") ? (slice.image.bitPerSample ?: 8) : params.int('bits')
        parameters.alpha = params.int('alpha')
        parameters.thickness = params.int('thickness')
        parameters.color = params.color
        parameters.jpegQuality = params.int('jpegQuality')

        def uri = "/slice/crop.$format"

        if (parametersOnly)
            return [server:server, uri:uri, parameters:parameters]
        if (urlOnly)
            return makeGetUrl(uri, server, parameters)
        return makeRequest(uri, server, parameters)
    }

    def window(ImageInstance image, def params, def urlOnly = false) {
        window(image.baseImage.referenceSlice, params, urlOnly)
    }

    def window(AbstractImage image, def params, def urlOnly = false) {
        window(image.referenceSlice, params, urlOnly)
    }

    def window(SliceInstance slice, def params, def urlOnly = false) {
        window(slice.baseSlice, params, urlOnly)
    }

    def window(AbstractSlice slice, def params, def urlOnly = false) {
        def boundaries = [:]
        boundaries.topLeftX = Math.max((int) params.int('x'), 0)
        boundaries.topLeftY = Math.max((int) params.int('y'), 0)
        boundaries.width = params.int('w')
        boundaries.height = params.int('h')

        def withExterior = params.boolean('withExterior', false)
        if (!withExterior) {
            // Do not take part outside of the real image
            if(slice.image.width && (boundaries.width + boundaries.topLeftX) > slice.image.width) {
                boundaries.width = slice.image.width - boundaries.topLeftX
            }
            if(slice.image.height && (boundaries.height + boundaries.topLeftY) > slice.image.height) {
                boundaries.height = slice.image.height - boundaries.topLeftY
            }
        }

        boundaries.topLeftY = Math.max((int) (slice.image.height - boundaries.topLeftY), 0)
        params.boundaries = boundaries
        crop(slice, params, urlOnly)
    }

    private static def imsParametersFromAbstractImage(AbstractImage image) {
        if (!image.path) {
            throw new InvalidRequestException("Abstract image has no valid path.")
        }

        def server = image.getImageServerInternalUrl()
        def parameters = [
                fif: image.path,
                mimeType: image.uploadedFile.contentType
        ]
        return [server, parameters]
    }

    private static def imsParametersFromAbstractSlice(AbstractSlice slice) {
        if (!slice.path) {
            throw new InvalidRequestException("Abstract slice has no valid path.")
        }

        def server = slice.getImageServerInternalUrl()
        def parameters = [
                fif: slice.path,
                mimeType: slice.mimeType
        ]
        return [server, parameters]
    }

    private static def hmsInternalUrl() {
        def url = Holders.config.grails.hyperspectralServerURL
        return (Holders.config.grails.useHTTPInternally) ? url.replace("https", "http") : url;
    }

    private static def hmsParametersFromCompanionFile(CompanionFile cf) {
        if (!cf.path) {
            throw new InvalidRequestException("Companion file has no valid path.")
        }

        def server = hmsInternalUrl()
        def parameters = [fif: cf.path]
        return [server, parameters]
    }

    private static def filterParameters(parameters) {
        parameters.findAll { it.value != null && it.value != ""}
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

    private static byte[] makeRequest(def path, def server, def parameters, def httpMethod=null) {
        def final GET_URL_MAX_LENGTH = 512
        parameters = filterParameters(parameters)
        def url = makeGetUrl(path, server, parameters)
        def http = new HTTPBuilder(server)
        if ((url.size() < GET_URL_MAX_LENGTH && httpMethod == null) || httpMethod == "GET") {
            http.request(Method.GET, ContentType.BINARY) {
                uri.path = path
                uri.query = parameters

                response.success = { resp, stream ->
                    return IOUtils.toByteArray(stream)
                }

                response.'400' = {
                    throw new InvalidRequestException("$url returned a 400 Bad Request")
                }

                response.'404' = {
                    throw new ObjectNotFoundException("$url returned a 404 Not found")
                }

                response.'500' = {
                    throw new ServerException("$url returned a 500 Internal error")
                }
            }
        }
        else {
            http.request(Method.POST, ContentType.BINARY) {
                uri.path = path
                requestContentType = ContentType.URLENC
                body = parameters

                response.success = { resp, stream ->
                    return IOUtils.toByteArray(stream)
                }

                response.'400' = {
                    throw new InvalidRequestException("$url (with parameters $parameters) returned a 400 Bad Request")
                }

                response.'404' = {
                    throw new ObjectNotFoundException("$url (with parameters $parameters) returned a 404 Not found")
                }

                response.'500' = {
                    throw new ServerException("$url (with parameters $parameters) returned a 500 Internal error")
                }
            }
        }
    }

    private static def checkFormat(def format, def accepted) {
        if (!accepted)
            accepted = ['jpg']

        return (!accepted.contains(format)) ? accepted[0] : format
    }

    def checkType(def params, def accepted = null) {
        if (params.type && accepted?.contains(params.type))
            return params.type
        else if (params.draw)
            return 'draw'
        else if (params.mask)
            return 'mask'
        else if (params.alphaMask)
            return 'alphaMask'
        else
            return 'crop'
    }
}
