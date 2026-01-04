function setClass(id, cn) {
    //deal with IE
    var element = document.getElementById(id);
    if(element) {
        if(!element.getAttribute("class")) {
            element.setAttribute('className', cn);
        } else {
            element.setAttribute('class', cn);
        }
    }
}

function setStyle(id,styleKey,styleValue) {
    var element = document.getElementById(id);
    if(element) {
        element.style[styleKey] = styleValue;
    }
}

function isIE6() {
    var userAgent = navigator.userAgent;
    if(userAgent) {
        return userAgent.indexOf('MSIE 6.0') !== -1;
    } else {
        return false;
    }
}

function updateTitle(id, text, color) {
    var element = document.getElementById(id);
    if(element) {
        element.innerText = text;
        element.style.display = 'block';
        if(color !== undefined) {
            element.style.color = color;
        } else {
            element.style.color = 'black';
        }
    }
}

function getElementsByClassName(className, tagName) {
    if(document.getElementsByClassName) {
        return document.getElementsByClassName(className);
    }
    var elements = document.getElementsByTagName(tagName === undefined ? '*' : tagName);
    var result = [];
    var resultIndex = 0;
    for(var i=0;i<elements.length;i++) {
        var element = elements[i];
        var elementClassName = element.className;
        var cnArr = elementClassName.split(' ');
        for(var j=0;j<cnArr.length;j++) {
            if(cnArr[j] === className) {
                result[resultIndex++] = element;
                break;
            }
        }
    }
    return result;
}

function hasClass(element,className) {
    if(!element) {
        return false;
    }
    var cnArr = element.className.split(' ');
    for(var i=0;i<cnArr.length;i++) {
        var cn = cnArr[i];
        if(cn === className) {
            return true;
        }
    }
    return false;
}

function mergeCollectionOrArray(object1,object2) {
    var result = [];
    if(object1) {
        for(var i=0;i<object1.length;i++) {
            result.push(object1[i]);
        }
    }
    if(object2) {
        for(var i=0;i<object2.length;i++) {
            result.push(object2[i]);
        }
    }
    return result;
}