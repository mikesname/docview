
import axios from "axios";

/**
 * A data access object encapsulating the management API endpoints.
 */
let DAO = class {
  constructor(service, repoId) {
    this.service = service;
    this.repoId = repoId;
  }

  call(endpoint, data) {
    return axios.request({
      url: endpoint.url,
      method: endpoint.method,
      data: data,
      headers: {
        "ajax-ignore-csrf": true,
        "Content-Type": "application/json",
        "Accept": "application/json; charset=utf-8",
        "X-Requested-With": "XMLHttpRequest",
      },
      withCredentials: true,
    }).then(r => r.data);
  }

  listFiles(ds, stage, prefix, after) {
    return this.call(this.service.ImportFiles.listFiles(this.repoId, ds, stage, prefix, after));
  }

  getImportConfig(ds) {
    return this.call(this.service.ImportConfigs.get(this.repoId, ds));
  }

  saveImportConfig(ds, config) {
    return this.call(this.service.ImportConfigs.save(this.repoId, ds), config);
  }

  deleteImportConfig(ds) {
    return this.call(this.service.ImportConfigs.delete(this.repoId, ds));
  }

  ingestFiles(ds, paths, opts, commit) {
    let data = {
      config: opts,
      commit: commit,
      files: paths,
    };
    return this.call(this.service.ImportConfigs.ingestFiles(this.repoId, ds), data);
  }

  deleteFiles(ds, stage, paths) {
    return this.call(this.service.ImportFiles.deleteFiles(this.repoId, ds, stage), paths);
  }

  validateFiles(ds, stage, tagToPath) {
    return this.call(this.service.ImportFiles.validateFiles(this.repoId, ds, stage), tagToPath);
  }

  info(ds, stage, key, versionId) {
    return this.call(this.service.ImportFiles.info(this.repoId, ds, stage, key, versionId));
  }

  fileUrls(ds, stage, paths) {
    return this.call(this.service.ImportFiles.fileUrls(this.repoId, ds, stage), paths);
  }

  uploadHandle(ds, stage, fileSpec) {
    return this.call(this.service.ImportFiles.uploadHandle(this.repoId, ds, stage), fileSpec);
  }

  uploadFile(url, file, progressHandler) {
    const CancelToken = axios.CancelToken;
    const source = CancelToken.source();
    const progressFunc = progressHandler || function () { return true };

    return axios.put(url, file, {
      onUploadProgress: function (evt) {
        if (!progressFunc(evt)) {
          source.cancel();
        }
      },
      headers: {
        'Content-type': file.type,
        'Cache-Control': 120,
        'x-amz-meta-source': 'user',
      },
      cancelToken: source.token,
    }).then(r => r.status === 200)
      .catch(function (e) {
        if (axios.isCancel(e)) {
          console.log('Request canceled', file.name);
          return false;
        } else {
          throw e;
        }
      });
  }

  harvest(ds, config, fromLast) {
    return this.call(this.service.OaiPmhConfigs.harvest(this.repoId, ds, fromLast), config);
  }

  cancelHarvest(jobId) {
    return this.call(this.service.OaiPmhConfigs.cancelHarvest(this.repoId, jobId));
  }

  sync(ds, config) {
    return this.call(this.service.ResourceSyncConfigs.sync(this.repoId, ds), config);
  }

  cancelSync(jobId) {
    return this.call(this.service.ResourceSyncConfigs.cancelSync(this.repoId, jobId));
  }

  getSyncConfig(ds) {
    return this.call(this.service.ResourceSyncConfigs.get(this.repoId, ds));
  }

  saveSyncConfig(ds, config) {
    return this.call(this.service.ResourceSyncConfigs.save(this.repoId, ds), config);
  }

  deleteSyncConfig(ds) {
    return this.call(this.service.ResourceSyncConfigs.delete(this.repoId, ds));
  }

  testSyncConfig(ds, config) {
    return this.call(this.service.ResourceSyncConfigs.test(this.repoId, ds), config);
  }

  getConfig(ds) {
    return this.call(this.service.OaiPmhConfigs.get(this.repoId, ds));
  }

  saveConfig(ds, config) {
    return this.call(this.service.OaiPmhConfigs.save(this.repoId, ds), config);
  }

  deleteConfig(ds) {
    return this.call(this.service.OaiPmhConfigs.delete(this.repoId, ds));
  }

  testConfig(ds, config) {
    return this.call(this.service.OaiPmhConfigs.test(this.repoId, ds), config);
  }

  convert(ds, key, config) {
    return this.call(this.service.DataTransformations.convert(this.repoId, ds, key), config);
  }

  convertFileUrl(ds, stage, key) {
    return this.service.DataTransformations.convertFile(this.repoId, ds, stage, key).url;
  }

  cancelConvert(jobId) {
    return this.call(this.service.DataTransformations.cancelConvert(this.repoId, jobId));
  }

  getConvertConfig(ds) {
    return this.call(this.service.DataTransformations.getConfig(this.repoId, ds));
  }

  saveConvertConfig(ds, dtIds) {
    return this.call(this.service.DataTransformations.saveConfig(this.repoId, ds), dtIds);
  }

  listDataTransformations() {
    return this.call(this.service.DataTransformations.list(this.repoId));
  }

  getDataTransformation(id) {
    return this.call(this.service.DataTransformations.get(this.repoId, id));
  }

  createDataTransformation(generic, data) {
    return this.call(this.service.DataTransformations.create(this.repoId, generic), data);
  }

  updateDataTransformation(id, generic, data) {
    return this.call(this.service.DataTransformations.update(this.repoId, id, generic), data);
  }

  deleteDataTransformation(id) {
    return this.call(this.service.DataTransformations.delete(this.repoId, id));
  }

  listDatasets() {
    return this.call(this.service.ImportDatasets.list(this.repoId));
  }

  datasetStats() {
    return this.call(this.service.ImportDatasets.stats(this.repoId));
  }

  createDataset(info) {
    return this.call(this.service.ImportDatasets.create(this.repoId), info);
  }

  updateDataset(ds, info) {
    return this.call(this.service.ImportDatasets.update(this.repoId, ds), info);
  }

  deleteDataset(ds) {
    return this.call(this.service.ImportDatasets.delete(this.repoId, ds));
  }
};

export default DAO;
