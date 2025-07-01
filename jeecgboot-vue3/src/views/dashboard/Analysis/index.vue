<template>
  <div class="page-header-index-wide" style="margin:50px;">
    <ul>
      <li v-for="item in listData"><h2>{{item}}</h2></li>
    </ul>
  </div>
</template>
<script lang="ts" setup>
  import { ref ,reactive, toRefs } from 'vue';

  const listData = ref([]);

  const items = [
    '    本系统是5G专网运营支撑系统，属于网络线数据管理系统，致力于改善云网数据管理手段。',
    '    本系统对5G专网运营管理、业务流程相关资料文档以及项目相关资料和现场配置文档进行有效管理，方便检索查询。',
    '    本系统支持对接5G专网相关平台，同步数据。',
    '    本系统支持各类数据维护、导出和报表展示。',
    '    本系统将根据使用人的需求，实时展示相关统计报表。',
  ]

  listData.value.push(...items);
</script>
