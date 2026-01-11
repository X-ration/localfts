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

// 发送 POST 请求
function sendPostFormRequest(url, data, isAsync, func) {
  var xhr = createXHR();
  if (!xhr) return;

  // 监听请求状态变化
  xhr.onreadystatechange = function() {
    func(xhr);
  };

  // 初始化请求（POST 方法，目标 URL，异步请求）
  xhr.open("POST", url, isAsync);

  // 设置请求头：POST 请求需指定 Content-Type，否则服务器可能无法解析数据
  // 表单格式数据通常用 application/x-www-form-urlencoded
  xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
  xhr.setRequestHeader("Accept", "application/json");

  // 发送数据（格式为 key=value&key2=value2，与表单提交格式一致）
  xhr.send(data);
}

function abortXhr() {
  if(xhr && xhr.readyState != 4) {
     xhr.abort();
  }
}