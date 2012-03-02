Storage.prototype.setObject = function(key, value) {
    this.setItem(key, JSON.stringify(value));
}

Storage.prototype.getObject = function(key) {
    return JSON.parse(this.getItem(key));
}

var ApplicationView = Backbone.View.extend({

    tagName : "div",
    className : "layout",
    components : {},
    panelsConfiguration : [
        {key : "toolbar-panel", linkID : "toggle-toolbar-panel", name : "Toolbar", className : "toolbarPanel", value : { visible : true , position : { top : 75}, align : "center"}},
        {key : "overview-panel", linkID : "toggle-overview-panel", name : "Overview", className : "overviewPanel", value : { visible : true , position : { right : 20, top : 325}}},
        {key : "ontology-panel", linkID : "toggle-ontology-panel", name : "Ontology", className : "ontologypanel", value : { visible : true , position : { left : 20, top : 280}}},
        {key : "layer-panel", linkID : "toggle-layer-panel", name : "Layer switcher", className : "layerSwitcherPanel", value : { visible : false , position : { right : 20, top : 100}}},
        {key : "filters-panel", linkID : "toggle-filters-panel", name : "Filters", className : "imageFiltersPanel", value : { visible : false , position : { right : 20, bottom : 15}}}
    ],
    events: {
        "click #undo":          "undo",
        "click #redo":          "redo"
    },
    /**
     *  UNDO the last command
     */
    undo : function () {
        window.app.controllers.command.undo();
    },

    /**
     * REDO the last command
     */
    redo : function () {
        window.app.controllers.command.redo();
    },

    toggleVisibility : function (item) {
        var preference = localStorage.getObject(item.key);
        preference.visible = !preference.visible;
        localStorage.setObject(item.key , preference);
        this.updateMenuItem(item);

    },
    updateMenuItem : function (item) {
        var preference = localStorage.getObject(item.key);
        if (preference != undefined && preference.visible != undefined && preference.visible == true) {
            $("#"+item.linkID).html("<i class='icon-eye-close' /> " + item.name);
            $("."+item.className).each(
                    function( intIndex ){
                        $(this).show('fast');
                    }
            );
        }
        else {
            $("#"+item.linkID).html("<i class='icon-eye-open' /> " + item.name);
            $("."+item.className).each(
                    function( intIndex ){
                        $(this).hide('fast');
                    }
            );
        }
    },
    /**
     * ApplicationView constructor. Call the initialization of its components
     * @param options
     */
    initialize: function(options) {
    },
    /**
     * Render the html into the DOM element associated to the view
     * @param tpl
     */
    doLayout: function(tpl, renderCallback) {
        $(this.el).html(_.template(tpl, {}));
        _.each(this.components, function (component) {
            component.render();
        });

        renderCallback.call();
        return this;
    },
    /**
     * Grab the layout and call ask for render
     */
    render : function(renderCallback) {
        this.initComponents();
        var self = this;
        require([
            "text!application/templates/BaseLayout.tpl.html"
        ],
                function(tpl) {
                    self.doLayout(tpl, renderCallback);
                });
        return this;
    },
    initPreferences : function () {
        _.each(this.panelsConfiguration, function (item) {
            if (localStorage.getObject(item.key)) return;
            localStorage.setObject(item.key, item.value);
        });
    },
    applyPreferences : function() {
        var self = this;
        _.each(self.panelsConfiguration, function (item) {
            self.updatePanelPosition(item, true);
            self.updateMenuItem(item);
        });
    },
    updatePanelPosition : function (item, triggerMoveEvent) {
        var preference = localStorage.getObject(item.key);
        if (preference == undefined) return;

        // Alignment
        if (preference.align != undefined && preference.align == "center") {
            var panelWidth = $("."+item.className).width();
            var windowWidth = $(window).width();
            var leftPosition = (windowWidth / 2) - (panelWidth / 2);
            preference.position.left = leftPosition;
            localStorage.setObject(item.key, preference);
        }

        // Position
        var position = preference.position;
        if (position.top == undefined) position.top = '';
        $("."+item.className).css("top", position.top);
        if (position.left == undefined) position.left = '';
        $("."+item.className).css("left", position.left);
        if (position.right == undefined) position.right = '';
        $("."+item.className).css("right", position.right);
        if (position.bottom == undefined) position.bottom = '';
        $("."+item.className).css("bottom", position.bottom);

        if (triggerMoveEvent) $("."+item.className).trigger("movedProgramatically");
    },
    updatePosition : function (className, position, triggerMoveEvent) {
        var self = this;
        var panelConfig = _.find(self.panelsConfiguration, function (item) {
            return item.className == className;
        });
        if (!panelConfig) return;
        var preference = localStorage.getObject(panelConfig.key);
        preference.position = position;
        localStorage.setObject(panelConfig.key, preference);
        self.updatePanelPosition(panelConfig, triggerMoveEvent);
    },
    initUserMenu : function () {
        var self = this;
        //Init user menu
        require([
            "text!application/templates/MenuDropDown.tpl.html"
        ],function(tpl) {
            $("#menu-right").append(tpl);
            $("#logout").click(function() {
                window.app.controllers.auth.logout();
                return false;
            });
            $("#loggedUser").html(window.app.status.user.model.prettyName());
            _.each(self.panelsConfiguration, function (item){
                self.updateMenuItem(item);
                $("#"+item.linkID).on('click',function(){
                    self.toggleVisibility(item);
                    self.updatePanelPosition(item, true);
                });
            });
            $("#toggle-floating-panel").on("click", function(){
                var key = "toggle-floating-panel";
                var preference = localStorage.getObject(key);
                preference.activated = !preference.activated;
                localStorage.setObject(key, preference);
            });
        });

    },
    /**
     * Initialize the components of the application
     */
    initComponents : function() {
        var self = this;
        require([
            "text!application/templates/upload/UploadComponent.tpl.html",
            "text!application/templates/WarehouseComponent.tpl.html",
            "text!application/templates/explorer/ExplorerComponent.tpl.html",
            "text!application/templates/AdminComponent.tpl.html",
            "text!application/templates/activity/ActivityComponent.tpl.html"
        ],
                function(uploadTpl, warehouseTpl, explorerTpl, adminTpl, activityTpl) {
                    self.components.activity = new Component({
                        el : "#content",
                        template : _.template(activityTpl, {}),
                        buttonAttr : {
                            elButton : "activity-button",
                            buttonText : "Activity",
                            buttonWrapper : "#menu",
                            dataContent : "Activity feed !",
                            dataTitle : "Activity",
                            icon : "ui-icon-circle-arrow-s",
                            route : "#activity"
                        },
                        divId : "activity"
                    });
                    self.components.upload = new Component({
                        el : "#content",
                        template : _.template(uploadTpl, {}),
                        buttonAttr : {
                            elButton : "upload-button",
                            buttonText : "Upload",
                            buttonWrapper : "#menu",
                            dataContent : "Send your data !",
                            dataTitle : "Upload",
                            icon : "ui-icon-circle-arrow-s",
                            route : "#upload"
                        },
                        divId : "upload"
                    });
                    self.components.warehouse = new Component({
                        el : "#content",
                        template : _.template(warehouseTpl, {}),
                        buttonAttr : {
                            elButton : "warehouse-button",
                            buttonText : "Organize",
                            buttonWrapper : "#menu",
                            dataContent : "Organize your projects, images, etc...",
                            dataTitle : "Organize",
                            icon : "ui-icon-wrench",
                            route : "#project"
                        },
                        divId : "warehouse"
                    });
                    self.components.explorer = new Component({
                        el : "#content",
                        template : _.template(explorerTpl, {}),
                        buttonAttr : {
                            elButton : "explorer-button",
                            buttonText : "Explore",
                            buttonWrapper : "#menu",
                            dataContent : "View your data",
                            dataTitle : "Explore",
                            icon : "ui-icon-image",
                            route : "#explorer"
                        },
                        divId : "explorer",
                        activate: function () {
                            if (window.app.status.currentProject == undefined)
                                $("#explorer > .noProject").show();
                            else
                                $("#explorer > .noProject").hide();
                            $("#" + this.divId).show();
                            $("#" + this.buttonAttr.elButton).addClass("ui-state-disabled");
                        }
                    });
                    /*self.components.admin = new Component({
                     el : "#content",
                     template : _.template(adminTpl, {}),
                     buttonAttr : {
                     elButton : "admin-button",
                     buttonText : "Admin",
                     buttonWrapper : "#menu",
                     icon : "ui-icon-wrench",
                     route : "#admin/users"
                     },
                     divId : "admin"
                     });*/

                    /*self.components.logout = new Component({
                     el : "#content",
                     template : "",
                     buttonAttr : {
                     elButton : "user-button",
                     buttonText :,
                     buttonWrapper : "#menu",
                     dataContent : "we have to delete this popover for logout",
                     dataTitle : "huhu",
                     icon : "ui-icon-power",
                     route : "#",
                     click :
                     },
                     divId : "logout"
                     });*/
                });
    },
    /**
     * Show a component
     * @param Component the reference to the component
     */
    showComponent : function (component) {
        _.each(this.components, function (c) {
            if (c != component) c.deactivate();
        });
        $("#app").show();
        component.activate();
    }
});

ApplicationView.prototype.message =  function(title, message, type) {
    if (type == "" || type == undefined)
        type = 'alert-info';
    else
        type = 'alert-'+type;

    if(message!=undefined) {
        message.responseText && (message = message.responseText);
    }

    var tpl = '<div style="min-width: 200px" id="alert<%=   timestamp %>" class="alert <%=   type %> fade in" data-alert="alert"><a class="close" data-dismiss="alert">×</a><p><strong><%=   alert %></strong> <%=   message %></p></div>';
    var timestamp = new Date().getTime();
    $("#alerts").append(_.template(tpl, { alert : title, message : message, timestamp : timestamp, type : type}));
    $("#alert"+timestamp).alert();
    setTimeout(function(){
        //$("#alert"+timestamp).alert('close');
        $("#alert"+timestamp).remove();
    }, 3000);

}




