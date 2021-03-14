<script lang="ts">

export default {
    data: function(): object {
    return {
      panelSize: null,
    }
  },
  methods: {
    setPanelSize: function (arbitrarySize): void {
      this.panelSize = arbitrarySize;
    }
  }
}
</script>
