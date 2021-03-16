

import { shallowMount } from '@vue/test-utils';
import FilesTable from './_files-table.vue';


describe('FilesTable component', () => {
  let testFiles = [
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
  ];

  const wrapper = shallowMount(FilesTable, {
    propsData: {
      fileStage: 'input',
      files: testFiles,
      selected: {"hello.xml": testFiles[0]},
      loadingInfo: {"hello.xml": testFiles[0]}
    }
  });

  test('cell rendering ', () => {
    expect(wrapper.isVisible()).toBeTruthy();
    expect(wrapper.find("tbody tr").exists()).toBe(true);
  });

  test('select-all checkbox state', () => {
    // Select-all checkbox should have an indeterminate state when not all files
    // are selected
    expect(wrapper.find('thead input[type=checkbox]').element)
        .toHaveProperty('indeterminate', true);
  });
});
