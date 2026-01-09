if (!String.prototype.startsWith) {
    String.prototype.startsWith = function(search, pos) {
        pos = !pos || pos < 0 ? 0 : +pos;
        return this.substr(pos, search.length) === search;
    };
}

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

function setStyle(element,styleKey,styleValue) {
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

function getIEVersion() {
  var userAgent = navigator.userAgent.toLowerCase();
  var ie6_10Reg = /msie\s(\d+(\.\d+)?)/;
  var ie11Reg = /rv:(\d+(\.\d+)?)/;

  // 判断是否是 IE6~IE10
  if (ie6_10Reg.test(userAgent)) {
    // 提取版本号并转为数字类型
    var version = parseFloat(RegExp.$1);
    return version;
  }
  // 判断是否是 IE11：IE11的UA包含 trident/7.0 且包含 rv:11.0
  else if (userAgent.indexOf('trident/7.0') !== -1 && ie11Reg.test(userAgent)) {
    var version = parseFloat(RegExp.$1);
    return version;
  }
  // 不是IE浏览器
  else {
    return false;
  }
}

function getChromiumVersion() {
  var userAgent = navigator.userAgent;
  var chromeReg = /Chrome\/(\d+)\./i; // 正则匹配Chrome/后面的主版本号
  var match = userAgent.match(chromeReg);
  return match ? Number(match[1]) : false; // 提取成功返回数字版本号，失败返回0
}

function isFirefox() {
    var userAgent = navigator.userAgent;
    if(userAgent) {
        return userAgent.indexOf('Gecko') !== -1 && userAgent.indexOf('like Gecko') === -1;
    } else {
        return false;
    }
}

function zeroFill(num) {
    num = Number(num);
    return num < 10 ? '0' + num : num;
}

// 时间格式化核心函数
// date: 日期对象（不传则默认当前时间）
// format: 格式字符串，支持 yyyy(年)、MM(月)、dd(日)、HH(时)、mm(分)、ss(秒)
function formatTime(date, format) {
    date = date || new Date();
    format = format || 'yyyy-MM-dd HH:mm:ss';

    // 获取时间各部分
    var year = date.getFullYear(); // 四位年份（IE6不支持getYear，必须用getFullYear）
    var month = zeroFill(date.getMonth() + 1); // 月份从0开始，需+1
    var day = zeroFill(date.getDate());
    var hour = zeroFill(date.getHours()); // 小时（24小时制）
    var minute = zeroFill(date.getMinutes());
    var second = zeroFill(date.getSeconds());

    format = format.replace('yyyy', year);
    format = format.replace('MM', month);
    format = format.replace('dd', day);
    format = format.replace('HH', hour);
    format = format.replace('mm', minute);
    format = format.replace('ss', second);

    return format;
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