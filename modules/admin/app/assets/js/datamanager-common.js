"use strict";

function sequential(func, arr, index) {
  if (index >= arr.length) return Promise.resolve();
  return func(arr[index])
    .then(() => sequential(func, arr, index + 1));
}

// Bytes-to-human readable string from:
// https://stackoverflow.com/a/14919494/285374
Vue.filter("humanFileSize", function (bytes, si) {
  let f = (bytes, si) => {
    let thresh = si ? 1000 : 1024;
    if (Math.abs(bytes) < thresh) {
      return bytes + ' B';
    }
    let units = si
      ? ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
      : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
    let u = -1;
    do {
      bytes /= thresh;
      ++u;
    } while (Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(1) + ' ' + units[u];
  };
  return _.memoize(f)(bytes, si);
});

Vue.filter("stageName", function(code, config) {
  switch (code) {
    case "oaipmh": return "Harvesting";
    case "upload": return "Uploads";
    case "rs": return "ResourceSync";
    default: return code;
  }
});

Vue.filter("decodeUri", function(s) {
  return decodeURI(s);
});

Vue.filter("prettyDate", function (time) {
  let f = time => {
    let m = luxon.DateTime.fromISO(time);
    return m.isValid ? m.toRelative() : "";
  };
  return _.memoize(f)(time);
});
