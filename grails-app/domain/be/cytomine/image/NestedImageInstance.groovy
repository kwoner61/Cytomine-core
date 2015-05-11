package be.cytomine.image

import be.cytomine.Exception.AlreadyExistException
import be.cytomine.project.Project
import be.cytomine.security.User
import be.cytomine.utils.JSONUtils
import org.restapidoc.annotation.RestApiObject
import org.restapidoc.annotation.RestApiObjectField

/**
 * Created by IntelliJ IDEA.
 * User: lrollus
 * Date: 18/05/11
 * Time: 8:33
 * An nestedImageInstance is a subimage of an already existing image instance
 */
@RestApiObject(name = "nested image instance", description = "An sub image from an image instance (sub area, same image transformed,...)")
class NestedImageInstance extends ImageInstance implements Serializable {

    //stack stuff
    @RestApiObjectField(description = "The image source for this sub-image")
    ImageInstance parent

    @RestApiObjectField(description = "Top x position of this image on the sub-image", mandatory = false)
    Integer x

    @RestApiObjectField(description = "Top y position of this image on the sub-image", mandatory = false)
    Integer y

    static belongsTo = [AbstractImage, Project, User]

    static constraints = {
        parent nullable: false
        x nullable: false
        y nullable: false
    }

    static mapping = {
        id generator: "assigned"
        baseImage fetch: 'join'
        sort "id"
    }

    /**
     * Check if this domain will cause unique constraint fail if saving on database
     */
    void checkAlreadyExist() {
        NestedImageInstance.withNewSession {
            NestedImageInstance imageAlreadyExist = NestedImageInstance.findByBaseImageAndParentAndProject(baseImage,parent,project)
            if (imageAlreadyExist != null && (imageAlreadyExist.id != id)) {
                throw new AlreadyExistException("Nested Image " + baseImage?.filename + " already map with image " + parent?.baseImage?.filename + "in project " + project?.name)
            }
        }
    }

    /**
     * Insert JSON data into domain in param
     * @param domain Domain that must be filled
     * @param json JSON containing data
     * @return Domain with json data filled
     */
    static NestedImageInstance insertDataIntoDomain(def json, def domain = new NestedImageInstance()) {

        domain = (NestedImageInstance)ImageInstance.insertDataIntoDomain(json,domain)
        domain.parent = JSONUtils.getJSONAttrDomain(json, "parent", new ImageInstance(), true)
        domain.x =  JSONUtils.getJSONAttrInteger(json,"x",0)
        domain.y =  JSONUtils.getJSONAttrInteger(json,"y",0)
        return domain;
    }

    /**
     * Define fields available for JSON response
     * @param domain Domain source for json value
     * @return Map with fields (keys) and their values
     */
    static def getDataFromDomain(def domain) {
        def returnArray = ImageInstance.getDataFromDomain(domain)
        returnArray['parent'] = domain?.parent?.id
        returnArray['x'] = domain?.x
        returnArray['y'] = domain?.y
        return returnArray
    }

}
