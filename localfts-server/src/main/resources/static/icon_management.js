//requires common.js
var GLOBAL_ICON_OBJECT = {};

function registerIcon(name, prefix, suffix) {
    GLOBAL_ICON_OBJECT[name] = {'prefix': prefix, 'suffix': suffix};
}

function getIconSrc(contextPath, name, color) {
    var iconObject = GLOBAL_ICON_OBJECT[name];
    if(!iconObject) {
        return null;
    }
    var fileName = iconObject.prefix;
    if(color) {
        fileName = fileName + '_' + color;
    }
    if(GLOBAL_IE_VERSION && GLOBAL_IE_VERSION < 8) {
        if(iconObject.suffix === 'png') {
            fileName = fileName + '_png8';
        }
    }
    if(!iconObject.suffix.startsWith('.')) {
        fileName = fileName + '.';
    }
    fileName = fileName + iconObject.suffix;
    return contextPath + '/static/icon/' + fileName;
}