package be.cytomine.image

import be.cytomine.Exception.InvalidRequestException
import be.cytomine.command.AddCommand
import be.cytomine.command.Command
import be.cytomine.command.DeleteCommand
import be.cytomine.command.EditCommand
import be.cytomine.command.Transaction
import be.cytomine.security.SecUser
import be.cytomine.utils.ModelService
import be.cytomine.utils.Task

import java.nio.file.Paths

import static org.springframework.security.acls.domain.BasePermission.READ
import static org.springframework.security.acls.domain.BasePermission.WRITE

class CompanionFileService extends ModelService {

    static transactional = true
    def cytomineService
    def securityACLService
    def abstractImageService

    def currentDomain() {
        return CompanionFile
    }

    def read(def id) {
        CompanionFile file = CompanionFile.read(id)
        if (file) {
            if (!abstractImageService.hasRightToReadAbstractImageWithProject(file.image)) //TODO: improve
                securityACLService.checkAtLeastOne(file, READ)
        }
        file
    }

    def list(AbstractImage image) {
        if (!abstractImageService.hasRightToReadAbstractImageWithProject(image)) //TODO: improve
            securityACLService.checkAtLeastOne(image, READ)
        CompanionFile.findAllByImage(image, [cache: false])
    }

    def list(UploadedFile uploadedFile) {
        securityACLService.checkAtLeastOne(uploadedFile, READ)
        CompanionFile.findAllByUploadedFile(uploadedFile)
    }

    def add(def json) {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkUser(currentUser)

        Command c = new AddCommand(user: currentUser)
        executeCommand(c, null, json)
    }

    def update(CompanionFile file, def json) {
        securityACLService.checkAtLeastOne(file, WRITE)
        SecUser currentUser = cytomineService.getCurrentUser()

        Command c = new EditCommand(user: currentUser)
        executeCommand(c, file, json)
    }

    def delete(CompanionFile file, Transaction transaction = null, Task task = null, boolean printMessage = true) {
        securityACLService.checkAtLeastOne(file, WRITE)
        SecUser currentUser = cytomineService.getCurrentUser()

        Command c = new DeleteCommand(user: currentUser, transaction: transaction)
        executeCommand(c, file, null)
    }

    def getUploader(def id) {
        CompanionFile file = read(id)
        return file?.uploadedFile?.user
    }

    def getStringParamsI18n(def domain) {
        return [domain.id, domain.originalFilename]
    }

    def addProfile(AbstractImage image) {
        if (image.hasProfile()) {
            throw new InvalidRequestException("Image $image already has a profile")
        }

        if (image.dimensions.size() != 3) {
            throw new InvalidRequestException("Image $image is not a 3D image")
        }

        def filename = "profile.hdf5"
        def extension = "hdf5"
        def contentType = "application/x-hdf5"
        def destinationPath = Paths.get(new Date().getTime().toString(), filename).toString()

        UploadedFile parent = image.uploadedFile
        UploadedFile uf = new UploadedFile(parent: parent, imageServer: parent.imageServer, contentType: contentType,
                storage: parent.storage, user: cytomineService.currentUser, originalFilename: filename,
                ext: extension, size: 0, status: UploadedFile.Status.UPLOADED.code, filename: destinationPath).save(flush: true, failOnError: true)
        CompanionFile cf = new CompanionFile(uploadedFile: uf, image: image, originalFilename: filename,
                filename: filename, type: "HDF5").save(flush: true, failOnError: true)

        try {
            imageServerService.makeHDF5(image.id, cf.id, uf.id)
        }
        catch (Exception e) {
            uf.status = UploadedFile.Status.ERROR_CONVERSION.code
            uf.save(flush: true)
        }
        return cf
    }

    def imageServerService
}
