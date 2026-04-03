//Requires common.css
//Requires common.js

var zIndex = 1;
var zIndexObject = {};

function showDialog(id) {
    var element = document.getElementById(id);
    if(element) {
        var hziDialog = getHighestZIndexDialog();
        if(hziDialog != element) {
            zIndexObject[id] = zIndex;
            element.style.zIndex = zIndex;
            zIndex++;
        }
        setClassById(id, 'show');
        if(IE_VERSION && IE_VERSION < 8) {
            var allSelect = document.getElementsByTagName('select');
            if(allSelect) {
                for(var i=0;i<allSelect.length;i++) {
                    allSelect[i].style.display = 'none';
                }
            }
        }
        var elementSelect = element.getElementsByTagName('select');
        if(elementSelect) {
            for(var i=0;i<elementSelect.length;i++) {
                elementSelect[i].style.display = '';
            }
        }
    }
}

function hideDialog(id) {
    var element = document.getElementById(id);
    if(element) {
        delete zIndexObject[id];
        var hasKey = false;
        for(var key in zIndexObject) {
            if(zIndexObject.hasOwnProperty(key)) {
                hasKey = true;
                break;
            }
        }
        setClassById(id, 'hidden');
        if(IE_VERSION && IE_VERSION < 8) {
            if(hasKey) {
                var hziDialog = getHighestZIndexDialog(true);
                var elementSelect = hziDialog.getElementsByTagName('select');
                if(elementSelect) {
                    for(var i=0;i<elementSelect.length;i++) {
                        elementSelect[i].style.display = '';
                    }
                }
            } else {
                var allSelect = document.getElementsByTagName('select');
                if(allSelect) {
                    for(var i=0;i<allSelect.length;i++) {
                        allSelect[i].style.display = '';
                    }
                }
            }
        }
    }
}

function getHighestZIndexDialog(onlyShow) {
    var showDialogs = getElementsByClassName('show', 'div');
    var dialogs = showDialogs;
    if(!onlyShow) {
        var hiddenDialogs = getElementsByClassName('hidden', 'div');
        dialogs = mergeCollectionOrArray(showDialogs, hiddenDialogs);
    }
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