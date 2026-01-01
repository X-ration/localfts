function setClass(id, cn) {
    //deal with IE
    var element = document.getElementById(id);
    if(!element.getAttribute("class")) {
        element.setAttribute('className', cn);
    } else {
        element.setAttribute('class', cn);
    }
}