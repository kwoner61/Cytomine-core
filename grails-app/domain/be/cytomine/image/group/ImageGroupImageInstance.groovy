package be.cytomine.image.group

import be.cytomine.CytomineDomain
import be.cytomine.Exception.AlreadyExistException
import be.cytomine.image.ImageInstance
import be.cytomine.utils.JSONUtils

class ImageGroupImageInstance extends CytomineDomain implements Serializable {

    ImageGroup group

    ImageInstance image

    static constraints = {
    }

    static mapping = {
        id(generator: 'assigned', unique: true)
        sort 'id'
    }

    @Override
    void checkAlreadyExist() {
        ImageGroupImageInstance.withNewSession {
            ImageGroupImageInstance igii = ImageGroupImageInstance.findByGroupAndImage(group, image)
            if (igii != null && igii.id != id) {
                throw new AlreadyExistException("Image $image already linked to group $group")
            }
        }
    }

    static ImageGroupImageInstance insertDataIntoDomain(def json, def domain = new ImageGroupImageInstance()) {
        domain.id = JSONUtils.getJSONAttrLong(json,'id',null)
        domain.deleted = JSONUtils.getJSONAttrDate(json, "deleted")
        domain.group = JSONUtils.getJSONAttrDomain(json, "group", new ImageGroup(), true)
        domain.image = JSONUtils.getJSONAttrDomain(json, "image", new ImageInstance(), true)
        return domain
    }

    /**
     * Define fields available for JSON response
     * @param domain Domain source for json value
     * @return Map with fields (keys) and their values
     */
    static def getDataFromDomain(def domain) {
        def returnArray = CytomineDomain.getDataFromDomain(domain)
        returnArray['image'] = domain?.image?.id
        returnArray['group'] = domain?.group?.id
        returnArray['groupName'] = domain?.group?.name
        returnArray
    }

    public CytomineDomain container() {
        return group.container();
    }
}
