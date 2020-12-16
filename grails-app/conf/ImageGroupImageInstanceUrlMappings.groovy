class ImageGroupImageInstanceUrlMappings {

    static mappings = {
        "/api/imagegroup/$id/imagegroupimageinstance.$format"(controller:"restImageGroupImageInstance"){
            action = [GET: "listByImageGroup"]
        }
        "/api/imageinstance/$id/imagegroupimageinstance.$format"(controller:"restImageGroupImageInstance"){
            action = [GET: "listByImageInstance"]
        }
        "/api/imagegroup/$group/imageinstance/$image.$format"(controller:"restImageGroupImageInstance"){
            action = [POST:"add", DELETE:"delete", GET:"show"]
        }
    }
}
