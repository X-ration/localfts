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