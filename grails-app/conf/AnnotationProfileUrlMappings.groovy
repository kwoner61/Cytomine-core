class AnnotationProfileUrlMappings {

    static mappings = {
        "/api/annotation/$id/profile.$format"(controller: "restAnnotationProfile") {
            action = [GET: "profile"]
        }

        "/api/annotation/$id/profile/projections.$format"(controller: "restAnnotationProfile") {
            action = [GET: "projections"]
        }

        "/api/annotation/$id/profile/min-projection.$format"(controller: "restAnnotationProfile") {
            action = [GET: "minProjection"]
        }

        "/api/annotation/$id/profile/max-projection.$format"(controller: "restAnnotationProfile") {
            action = [GET: "maxProjection"]
        }

        "/api/annotation/$id/profile/average-projection.$format"(controller: "restAnnotationProfile") {
            action = [GET: "averageProjection"]
        }

    }
}
