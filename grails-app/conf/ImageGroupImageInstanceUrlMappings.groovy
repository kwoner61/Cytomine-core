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

        "/api/imagegroup/$group/imageinstance/$image/previous.$format"(controller:"restImageGroupImageInstance"){
            action = [GET:"previous"]
        }

        "/api/imagegroup/$group/imageinstance/$image/next.$format"(controller:"restImageGroupImageInstance"){
            action = [GET:"next"]
        }
    }
}
