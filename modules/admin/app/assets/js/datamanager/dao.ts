
import axios from "axios";


interface OaiPmhConfig {
  url: string,
  format: string,
  set?: string,
}

interface ResourceSyncConfig {
  url: string,
  filter?: string,
}

interface ImportConfig {
  allowUpdates: boolean,
  tolerant: boolean,
  properties?: string,
  defaultLang?: string,
  logMessage: string,
  comments?: string,
}

interface TransformationList {
  mappings: string[],
  force: boolean,
}

interface ConvertSpec {
  mappings: [string, string][],
  force: boolean,
}

type ConvertConfig = TransformationList | ConvertSpec;

interface JobMonitor {
  jobId: string,
  url: string,
}

interface DataTransformationInfo {
  name: string,
  bodyType: string,
  body: string,
  comments: string
}

interface DataTransformation extends DataTransformationInfo{
  id: string,
  repoId?: string,
  created: string,
}

interface ImportDatasetInfo {
  id: string,
  name: string,
  src: string,
  fonds?: string,
  sync: boolean,
  notes?: string,
}

interface ImportDataset extends ImportDatasetInfo {
  repoId: string,
  created: string,
}

/**
 * A data access object encapsulating the management API endpoints.
 */
class DAO {
  service: any;
  repoId: string;

  static DONE_MSG: string = "Done";
  static ERR_MSG: string = "Error";

  constructor(service: object, repoId: string) {
    this.service = service;
    this.repoId = repoId;
  }

  private static call(endpoint: {url: string, method: any}, data?: object) {
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

  listFiles(ds: string, stage: string, prefix: string, after: string): Promise<Object> {
    return DAO.call(this.service.ImportFiles.listFiles(this.repoId, ds, stage, prefix, after));
  }

  getImportConfig(ds: string): Promise<ImportConfig | null> {
    return DAO.call(this.service.ImportConfigs.get(this.repoId, ds));
  }

  saveImportConfig(ds: string, config: ImportConfig): Promise<ImportConfig> {
    return DAO.call(this.service.ImportConfigs.save(this.repoId, ds), config);
  }

  deleteImportConfig(ds: string): Promise<void> {
    return DAO.call(this.service.ImportConfigs.delete(this.repoId, ds));
  }

  ingestFiles(ds: string, paths: string[], opts: ImportConfig, commit: boolean): Promise<JobMonitor> {
    let data = {
      config: opts,
      commit: commit,
      files: paths,
    };
    return DAO.call(this.service.ImportConfigs.ingestFiles(this.repoId, ds), data);
  }

  deleteFiles(ds: string, stage: string, paths: string[]): Promise<{deleted: number}> {
    return DAO.call(this.service.ImportFiles.deleteFiles(this.repoId, ds, stage), paths);
  }

  validateFiles(ds: string, stage: string, tagToPath: object): Promise<Object[]> {
    return DAO.call(this.service.ImportFiles.validateFiles(this.repoId, ds, stage), tagToPath);
  }

  info(ds: string, stage: string, key: string, versionId?: string): Promise<Object> {
    return DAO.call(this.service.ImportFiles.info(this.repoId, ds, stage, key, versionId));
  }

  fileUrls(ds: string, stage: string, paths: string[]): Promise<Object> {
    return DAO.call(this.service.ImportFiles.fileUrls(this.repoId, ds, stage), paths);
  }

  uploadHandle(ds: string, stage: string, fileSpec: object) {
    return DAO.call(this.service.ImportFiles.uploadHandle(this.repoId, ds, stage), fileSpec);
  }

  uploadFile(url: string, file: File, progressHandler: Function) {
    const CancelToken = axios.CancelToken;
    const source = CancelToken.source();
    const progressFunc = progressHandler || function () { return true };

    return axios.put(url, file, {
      onUploadProgress: function (evt): void {
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
      .catch(function (e): boolean {
        if (axios.isCancel(e)) {
          console.log('Request canceled', file.name);
          return false;
        } else {
          throw e;
        }
      });
  }

  harvest(ds: string, config: OaiPmhConfig, fromLast: boolean): Promise<JobMonitor> {
    return DAO.call(this.service.OaiPmhConfigs.harvest(this.repoId, ds, fromLast), config);
  }

  cancelHarvest(jobId: string): Promise<{ok: boolean}> {
    return DAO.call(this.service.OaiPmhConfigs.cancelHarvest(this.repoId, jobId));
  }

  sync(ds: string, config: ResourceSyncConfig): Promise<JobMonitor> {
    return DAO.call(this.service.ResourceSyncConfigs.sync(this.repoId, ds), config);
  }

  cancelSync(jobId: string): Promise<{ok: boolean}> {
    return DAO.call(this.service.ResourceSyncConfigs.cancelSync(this.repoId, jobId));
  }

  getSyncConfig(ds: string): Promise<ResourceSyncConfig | null> {
    return DAO.call(this.service.ResourceSyncConfigs.get(this.repoId, ds));
  }

  saveSyncConfig(ds: string, config: ResourceSyncConfig): Promise<ResourceSyncConfig> {
    return DAO.call(this.service.ResourceSyncConfigs.save(this.repoId, ds), config);
  }

  deleteSyncConfig(ds: string): Promise<void> {
    return DAO.call(this.service.ResourceSyncConfigs.delete(this.repoId, ds));
  }

  testSyncConfig(ds: string, config: ResourceSyncConfig): Promise<{ok: true}> {
    return DAO.call(this.service.ResourceSyncConfigs.test(this.repoId, ds), config);
  }

  getConfig(ds: string): Promise<OaiPmhConfig | null> {
    return DAO.call(this.service.OaiPmhConfigs.get(this.repoId, ds));
  }

  saveConfig(ds: string, config: OaiPmhConfig): Promise<OaiPmhConfig> {
    return DAO.call(this.service.OaiPmhConfigs.save(this.repoId, ds), config);
  }

  deleteConfig(ds: string): Promise<void> {
    return DAO.call(this.service.OaiPmhConfigs.delete(this.repoId, ds));
  }

  testConfig(ds: string, config: OaiPmhConfig): Promise<Object> {
    return DAO.call(this.service.OaiPmhConfigs.test(this.repoId, ds), config);
  }

  convert(ds: string, key: string, config: ConvertConfig): Promise<JobMonitor> {
    return DAO.call(this.service.DataTransformations.convert(this.repoId, ds, key), config);
  }

  convertFileUrl(ds: string, stage: string, key: string) {
    return this.service.DataTransformations.convertFile(this.repoId, ds, stage, key).url;
  }

  cancelConvert(jobId: string): Promise<{ok: boolean}> {
    return DAO.call(this.service.DataTransformations.cancelConvert(this.repoId, jobId));
  }

  getConvertConfig(ds: string): Promise<Object | null> {
    return DAO.call(this.service.DataTransformations.getConfig(this.repoId, ds));
  }

  saveConvertConfig(ds: string, dtIds: string[]): Promise<{ok: true}> {
    return DAO.call(this.service.DataTransformations.saveConfig(this.repoId, ds), dtIds);
  }

  listDataTransformations(): Promise<DataTransformation[]> {
    return DAO.call(this.service.DataTransformations.list(this.repoId));
  }

  getDataTransformation(id: string): Promise<DataTransformation> {
    return DAO.call(this.service.DataTransformations.get(this.repoId, id));
  }

  createDataTransformation(generic: boolean, data: DataTransformationInfo): Promise<DataTransformation> {
    return DAO.call(this.service.DataTransformations.create(this.repoId, generic), data);
  }

  updateDataTransformation(id: string, generic: boolean, data: DataTransformationInfo): Promise<DataTransformation> {
    return DAO.call(this.service.DataTransformations.update(this.repoId, id, generic), data);
  }

  deleteDataTransformation(id: string): Promise<void> {
    return DAO.call(this.service.DataTransformations.delete(this.repoId, id));
  }

  listDatasets(): Promise<ImportDataset[]> {
    return DAO.call(this.service.ImportDatasets.list(this.repoId));
  }

  datasetStats() {
    return DAO.call(this.service.ImportDatasets.stats(this.repoId));
  }

  createDataset(info: ImportDatasetInfo): Promise<ImportDataset> {
    return DAO.call(this.service.ImportDatasets.create(this.repoId), info);
  }

  updateDataset(ds: string, info: ImportDatasetInfo): Promise<ImportDataset> {
    return DAO.call(this.service.ImportDatasets.update(this.repoId, ds), info);
  }

  deleteDataset(ds: string): Promise<void> {
    return DAO.call(this.service.ImportDatasets.delete(this.repoId, ds));
  }
}

export default DAO;
