/**
    兼容IE6的字符串startsWith方法
*/
if (!String.prototype.startsWith) {
    String.prototype.startsWith = function(search, pos) {
        pos = !pos || pos < 0 ? 0 : +pos;
        return this.substr(pos, search.length) === search;
    };
}

/**
    兼容IE6的字符串trim方法
*/
if (!String.prototype.trim) {
    String.prototype.trim = function() {
        return this.replace(/^\s+|\s+$/g, '');
    };
}

/**
    兼容IE6的数组indexOf方法
*/
if (!Array.prototype.indexOf) {
    Array.prototype.indexOf = function (searchElement, fromIndex) {
        var array = this;
        if (array === null || array === undefined) {
            throw new TypeError('"this" is null or not defined');
        }
        var len = array.length;
        var index = 0;

        if (fromIndex !== null && fromIndex !== undefined) {
            index = Number(fromIndex);
            if (index === NaN) {
                index = 0;
            }
        }
        if (index >= len) {
            return -1;
        }
        var k = index < 0 ? Math.max(len + index, 0) : index;

        while(k<len) {
            if (array[k] === searchElement) {
                return k;
            }
            k++;
        }

        return -1;
    };
}

function selectOption(select,value,isValue) {
    if(select === undefined || value === undefined) {
        return;
    }
    if(isValue === undefined) {
        isValue = true;
    }
    var options = select.children;
    for(var i=0;i<options.length;i++) {
        var option = options[i];
        var shouldSelect = (isValue && option.value === value) || (!isValue && option.innerText === value);
        if(shouldSelect) {
            if(IE_VERSION && IE_VERSION < 7) {
                option.setAttribute("selected", "selected");
            } else {
                option.selected = true;
            }
            break;
        }
    }
}

function getSelectedOptionValue(select,isValue) {
    if(select === undefined) {
        return;
    }
    if(isValue === undefined) {
        isValue = true;
    }
    var options = select.children;
    for(var i=0;i<options.length;i++) {
        var option = options[i];
        if(option.selected) {
            return isValue ? option.value : option.innerText;
        }
    }
    return null;
}

function getElementsByName(tagName, name) {
    if(document.getElementsByName) {
        return document.getElementsByName(name);
    }
    //for IE, not supporting document.getElementsByName
    var collection = document.getElementsByTagName(tagName);
    var result = [];
    if(collection) {
        for(var i=0;i<collection.length;i++) {
            if(collection[i].getAttribute("name") == name) {
                result[result.length] = collection[i];
            }
        }
    }
    return result;
}

function getCheckedValue(name) {
    var elements = getElementsByName('input', name);
    for(var i=0;i<elements.length;i++) {
        var element = elements[i];
        if(element.checked) {
            return element.value;
        }
    }
    return null;
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

/**
    注意：IE6不支持，需要直接给style.[attr](驼峰式)赋值
*/
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

var IE_VERSION = getIEVersion();

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

var supported_date_time_formats = ['yyyy-MM-dd', 'yyyy-MM-dd HH:mm:ss'];
function isValidDateTime(str,format) {
    if (typeof str !== 'string') {
        return false;
    }
    format = format || 'yyyy-MM-dd HH:mm:ss';
    if (supported_date_time_formats.indexOf(format) === -1) {
        if(window.console) {
            console.error("Invalid format:" + format);
        }
        return false;
    }
    var regObject = {};
    regObject['yyyy-MM-dd'] = /^(\d{4})-(\d{2})-(\d{2})$/;
    regObject['yyyy-MM-dd HH:mm:ss'] = /^(\d{4})-(\d{2})-(\d{2})\s(\d{2}):(\d{2}):(\d{2})$/;
    var trimStr = str.replace(/^\s+|\s+$/g, '');
    if (trimStr.length === 0) {
        return false;
    }
    var reg = regObject[format];
    var matchResult = trimStr.match(reg);
    if (!matchResult) {
        return false;
    }
    var year = parseInt(matchResult[1], 10);
    var month = parseInt(matchResult[2], 10);
    var day = parseInt(matchResult[3], 10);
    var hour = matchResult[4] ? parseInt(matchResult[4], 10) : 0;
    var minute = matchResult[5] ? parseInt(matchResult[5], 10) : 0;
    var second = matchResult[6] ? parseInt(matchResult[6], 10) : 0;

    if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second <0 || second >59) {
        return false;
    }

    var dateStr = trimStr.replace(/-/g, '/');
    var dateObj = new Date(dateStr);
    if (
        dateObj.getFullYear() !== year ||
        dateObj.getMonth() + 1 !== month ||
        dateObj.getDate() !== day ||
        dateObj.getHours() !== hour ||
        dateObj.getMinutes() !== minute ||
        dateObj.getSeconds() !== second
    ) {
        return false;
    }

    return true;
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

function updateMsg(id, text, color) {
    var element = document.getElementById(id);
    if(element) {
        element.innerText = text;
        element.style.display = '';
        if(color !== undefined) {
            element.style.color = color;
        } else {
            element.style.color = 'black';
        }
    }
}

function formatDateDateStr(date) {
    if(!date) {
        return '';
    }
    var year = date.getFullYear();
    var month = date.getMonth() + 1;
    month = (month < 10 ? '0': '') + month;
    var day = (date.getDate() < 10 ? '0' : '') + date.getDate();
    return year + '-' + month + '-' + day;
}

function formatDateTimeStr(date) {
    if(!date) {
        return '';
    }
    var hour = (date.getHours() < 10 ? '0' : '') + date.getHours();
    var minute = (date.getMinutes() < 10 ? '0' : '') + date.getMinutes();
    var second = (date.getSeconds() < 10 ? '0' : '') + date.getSeconds();
    return hour + ':' + minute + ':' + second;
}

function formatTimeInputTimeStr(input) {
    if(!input) {
        return '';
    }
    var value = input.value;
    if(value === '') {
        return '';
    }
    if(value.split(':').length === 2) {
        var date = input.valueAsDate;
        var second = (date.getSeconds() < 10 ? '0' : '') + date.getSeconds();
        value = value + ':' + second;
    }
    return value;
}

function formatDateFullStr(date) {
    return formatDateDateStr(date) + ' ' + formatDateTimeStr(date);
}

function isDateAndTimeInputSupported() {
    var div = document.createElement('div');
    div.innerHTML = '<input type="date"><input type="time">';
    var input1 = div.children[0], input2 = div.children[1];
    var supportDate = input1.type !== 'text',
        supportTime = input2.type !== 'text';
    return supportDate && supportTime;
}

function removeElementById(id,parentId) {
  var element = document.getElementById(id);
  var parentElement = document.getElementById(parentId);
  if(element && parentElement) {
    parentElement.removeChild(element);
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