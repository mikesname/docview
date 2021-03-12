import {DateTime} from "luxon";

function humanFileSize(bytes, si) {
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
}

function prettyDate(time) {
  let f = time => {
    let m = DateTime.fromISO(time);
    return m.isValid ? m.toRelative() : "";
  };
  return _.memoize(f)(time);
}

export { humanFileSize, prettyDate };
