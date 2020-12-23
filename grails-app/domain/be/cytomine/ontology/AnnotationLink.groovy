package be.cytomine.ontology

import be.cytomine.AnnotationDomain
import be.cytomine.CytomineDomain
import be.cytomine.Exception.AlreadyExistException
import be.cytomine.image.ImageInstance
import be.cytomine.utils.JSONUtils

class AnnotationLink extends CytomineDomain implements Serializable {

    String annotationClassName

    Long annotationIdent

    AnnotationGroup group

    ImageInstance image

    static constraints = {
    }

    static mapping = {
        id(generator: 'assigned', unique: true)
        sort 'id'
    }

    void checkAlreadyExist() {
        AnnotationLink.withNewSession {
            AnnotationLink annotationLink = AnnotationLink.findByAnnotationIdentAndGroup(annotationIdent, group)
            if (annotationLink != null && annotationLink?.id != id) {
                throw new AlreadyExistException("AnnotationLink linking ${annotationLink?.annotationIdent} with ${annotationLink.group} already exist!")
            }

            annotationLink = AnnotationLink.findByAnnotationIdent(annotationIdent)
            if (annotationLink != null) {
                throw new AlreadyExistException("The annotation ${annotationIdent} is already linked to some annotation group !")
            }
        }
    }

    static AnnotationLink insertDataIntoDomain(def json, def domain = new AnnotationLink()) {
        domain.id = JSONUtils.getJSONAttrLong(json, 'id', null)
        domain.created = JSONUtils.getJSONAttrDate(json, 'created')
        domain.updated = JSONUtils.getJSONAttrDate(json, 'updated')
        domain.deleted = JSONUtils.getJSONAttrDate(json, 'deleted')

        domain.annotationClassName = JSONUtils.getJSONAttrStr(json, 'annotationClassName',true)
        domain.annotationIdent = JSONUtils.getJSONAttrLong(json,'annotationIdent',null)
        domain.group = JSONUtils.getJSONAttrDomain(json, "group", new AnnotationGroup(), true)
        domain.image = JSONUtils.getJSONAttrDomain(json, "image", new ImageInstance(), true)
        return domain
    }

    static def getDataFromDomain(def domain) {
        def returnArray = CytomineDomain.getDataFromDomain(domain)
        returnArray['annotationIdent'] = domain?.annotationIdent
        returnArray['annotationClassName'] = domain?.annotationClassName
        returnArray['group'] = domain?.group?.id
        returnArray['image'] = domain?.image?.id
        return returnArray
    }

    CytomineDomain container() {
        return group.container()
    }

    AnnotationDomain annotation() {
        return AnnotationDomain.getAnnotationDomain(annotationIdent, annotationClassName)
    }
}
