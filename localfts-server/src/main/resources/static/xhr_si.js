var xhr = null;
// 创建 XMLHttpRequest 对象（兼容 IE6）
function createXHR() {
  //保存全局引用
  //var xhr;
  // 现代浏览器及 IE7+ 支持原生 XMLHttpRequest
  if (window.XMLHttpRequest) {
    xhr = new XMLHttpRequest();
  } else {
    // IE6 及更早版本不支持原生 XMLHttpRequest，需使用 ActiveXObject
    try {
      xhr = new ActiveXObject("Microsoft.XMLHTTP"); // IE6 常用的 ActiveX 控件
    } catch (e) {
      console.error("无法创建 XMLHttpRequest 对象（不支持的浏览器）");
      return null;
    }
  }
  return xhr;
}

/** 发送 POST 请求
  @param errorFunc 错误处理函数，同时用于处理catch块捕获的异常(DOMException)和onerror事件(ErrorEvent)
  @param timeout 超时时间，只在异步请求中有效
  @param timeoutFunc 超时处理函数，只在异步请求中有效
*/
function sendPostFormRequest(url, data, isAsync, func, errorFunc, timeout, timeoutFunc) {
  var xhr = createXHR();
  if (!xhr) return;

  var ie6TimeoutTimer = null;

  // 监听请求状态变化
  xhr.onreadystatechange = function() {
    if(xhr && xhr.readyState === 4) {
      //兼容IE9
      try {
        var xhrStatus = xhr.status;
      } catch(e) {
        if(window.console) {
          console.error('获取XMLHttpRequest状态码时发生异常:', e);
        }
        return;
      }
      if(GLOBAL_IE_VERSION && GLOBAL_IE_VERSION < 7 && isAsync && timeout) {
        if(ie6TimeoutTimer) {
          clearTimeout(ie6TimeoutTimer);
          ie6TimeoutTimer = null;
        }
      };
      func(xhr);
    }
  };

  // 兼容IE9（必须在open和send之间设置timeout）
  // 初始化请求（POST 方法，目标 URL，异步请求）
  xhr.open("POST", url, isAsync);

  if(!GLOBAL_IE_VERSION || GLOBAL_IE_VERSION > 6) {
    if(errorFunc) {
      xhr.onerror = errorFunc;
    }
    if(isAsync && timeout) {
      xhr.timeout = timeout;
      if(timeoutFunc) {
        xhr.ontimeout = timeoutFunc;
      }
    }
  } else {
    if(isAsync && timeout) {
      ie6TimeoutTimer = setTimeout(function () {
        if(xhr && xhr.readyState !== 4) {
          xhr.abort();
          if(timeoutFunc) {
            timeoutFunc();
          }
        }
      }, timeout);
    }
  }

  // 设置请求头：POST 请求需指定 Content-Type，否则服务器可能无法解析数据
  // 表单格式数据通常用 application/x-www-form-urlencoded
  xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
  xhr.setRequestHeader("Accept", "application/json");

  // 发送数据（格式为 key=value&key2=value2，与表单提交格式一致）
  try {
    xhr.send(data);
  } catch (e) {
    if(window.console) {
      console.error('发起XHR请求失败', e);
    }
    if(errorFunc) {
      errorFunc(e);
    }
  }
}

function abortXhr() {
  if(xhr && xhr.readyState != 4) {
     xhr.abort();
  }
}