//Requires common.css
//Requires common.js

var zIndex = 1;
var zIndexObject = {};

function showDialog(id) {
    var element = document.getElementById(id);
    if(element) {
        var hzid = getHighestZIndexDialog();
        if(hzid != element) {
            zIndexObject[id] = zIndex;
            element.style.zIndex = zIndex;
            zIndex++;
        }
        setClass(id, 'show');
        if(IE_VERSION && IE_VERSION < 8) {
            var allSelect = document.getElementsByTagName('select');
            for(var i=0;i<allSelect.length;i++) {
                allSelect[i].style.display = 'none';
            }
        }
    }
}

function hideDialog(id) {
    var element = document.getElementById(id);
    if(element) {
        delete zIndexObject[id];
        /*var hasKey = false;
        for(var key in zIndexObject) {
            if(zIndexObject.hasOwnProperty(key)) {
                hasKey = true;
                break;
            }
        }
        if(!hasKey) {
            zIndex = 1;
        }*/
        setClass(id, 'hidden');
        if(IE_VERSION && IE_VERSION < 8) {
            var allSelect = document.getElementsByTagName('select');
            for(var i=0;i<allSelect.length;i++) {
                allSelect[i].style.display = '';
            }
        }
    }
}

function getHighestZIndexDialog() {
    var showDialogs = getElementsByClassName('show', 'div');
    var hiddenDialogs = getElementsByClassName('hidden', 'div');
    var dialogs = mergeCollectionOrArray(showDialogs, hiddenDialogs);
    if(dialogs && dialogs.length > 0) {
        var result = dialogs[0];
        for(var i=1;i<dialogs.length;i++) {
            var showDialog = dialogs[i];
            if(showDialog.style.zIndex > result.style.zIndex) {
                result = showDialog;
            }
        }
        return result;
    }
    return null;
}