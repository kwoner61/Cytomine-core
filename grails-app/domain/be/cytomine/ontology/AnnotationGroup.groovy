package be.cytomine.ontology

import be.cytomine.CytomineDomain
import be.cytomine.image.group.ImageGroup
import be.cytomine.project.Project
import be.cytomine.utils.JSONUtils

class AnnotationGroup extends CytomineDomain implements Serializable {

    Project project
    
    ImageGroup imageGroup
    
    String type = "SAME_OBJECT"

    static constraints = {
    }

    static mapping = {
        id(generator: 'assigned', unique: true)
        sort 'id'
    }

    void checkAlreadyExist() {

    }

    static AnnotationGroup insertDataIntoDomain(def json, def domain = new AnnotationGroup()) {
        domain.id = JSONUtils.getJSONAttrLong(json, 'id', null)
        domain.created = JSONUtils.getJSONAttrDate(json, 'created')
        domain.updated = JSONUtils.getJSONAttrDate(json, 'updated')
        domain.deleted = JSONUtils.getJSONAttrDate(json, 'deleted')

        domain.project = JSONUtils.getJSONAttrDomain(json, "project", new Project(), true)
        domain.imageGroup = JSONUtils.getJSONAttrDomain(json, "imageGroup", new ImageGroup(), true)
        domain.type = JSONUtils.getJSONAttrStr(json, 'type',true)
        return domain
    }

    static def getDataFromDomain(def domain) {
        def returnArray = CytomineDomain.getDataFromDomain(domain)
        returnArray['project'] = domain?.project?.id
        returnArray['imageGroup'] = domain?.imageGroup?.id
        returnArray['type'] = domain?.type
        return returnArray
    }

    CytomineDomain container() {
        return imageGroup.container()
    }
}
