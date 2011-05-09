/**
 * Created by IntelliJ IDEA.
 * User: lrollus
 * Date: 9/05/11
 * Time: 16:16
 * To change this template use File | Settings | File Templates.
 */
/**
 * Created by IntelliJ IDEA.
 * User: lrollus
 * Date: 8/04/11
 * Time: 13:29
 * To change this template use File | Settings | File Templates.
 */
var CommandModel = Backbone.Model.extend({

	url : function() {
		var base = 'api/command';
		var format = '.json';
        if (this.isNew()) return base + format;
		return base + (base.charAt(base.length - 1) == '/' ? '' : '/') + this.id + format;
	}
});


// define our collection
var CommandCollection = Backbone.Collection.extend({
    model: ProjectModel,

    url: function() {
        if (this.project != undefined) {
            return "api/project/" + this.project + "/last/" + this.max +".json";
        } else {
            return "api/command.json";
        }
    },
    initialize: function (options) {
        this.project = options.project;
        this.max = options.max;
    }
});