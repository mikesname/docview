import {ResourceSyncConfig, FileList} from "../types";

export default class DataManagerApi {
  constructor(service: object, repoId: string) {
    console.log("DataManagerApi mock constructor called");
  }

  listFiles(ds: string, stage: string, prefix: string, after?: string): Promise<FileList> {
    return Promise.resolve<FileList>({
          files: [
            {
              classifier: "test",
              key: "hello.xml",
              size: 100,
              lastModified: '2020-01-01T10:00:00',
            },
            {
              classifier: "test",
              key: "goodbye.xml",
              size: 100,
              lastModified: '2020-01-01T10:00:00',
            }
          ],
          truncated: false,
        }
    )
  }

  getSyncConfig(ds: string): Promise<ResourceSyncConfig | null> {
    return Promise.resolve(null);
  }
}
