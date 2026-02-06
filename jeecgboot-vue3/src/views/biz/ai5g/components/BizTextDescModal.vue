<!--手动录入text-->
<template>
  <BasicModal title="段落详情" destroyOnClose @register="registerModal" :canFullscreen="false" width="1000px" :footer="null">
    <div class="p-2">
      <div class="header">
        <a-tag color="#a9c8ff">
          <span>{{hitTextDescData.source}}</span>
        </a-tag>
      </div>
      <div class="content">
        <MarkdownViewer :value="hitTextDescData.content" />
      </div>
    </div>

  </BasicModal>
</template>

<script lang="ts">
  import { ref } from 'vue';
  import BasicModal from '@/components/Modal/src/BasicModal.vue';
  import { useModalInner } from '@/components/Modal';

  import BasicForm from '@/components/Form/src/BasicForm.vue';
  import { MarkdownViewer } from '@/components/Markdown';
  import { useGlobSetting } from "@/hooks/setting";

  export default {
    name: 'AiTextDescModal',
    components: {
      MarkdownViewer,
      BasicForm,
      BasicModal,
    },
    emits: ['success', 'register'],
    setup(props, { emit }) {
      let hitTextDescData = ref<any>({})
      
      //注册modal
      const [registerModal, { closeModal, setModalProps }] = useModalInner(async (data) => {
        if (data.score !== undefined) {
          hitTextDescData.value.source = 'score' + ' ' + data.score.toFixed(2);
        } else {
          hitTextDescData.value.source = data.title || '内容详情';
        }
        //替换图片宽度
        data.content = replaceImageWith(data.content || '');
        //替换图片domainUrl
        data.content = replaceDomainUrl(data.content || '');
        hitTextDescData.value.content = data.content;
        setModalProps({ title: '段落详情' });
      });
      const { domainUrl } = useGlobSetting();
      const replaceImageWith = markdownContent => {
        // 支持图片设置width的写法 ![](/static/jimuImages/screenshot_1617252560523.png =100)
        const regex = /!\[([^\]]*)\]\(([^)]+)=([0-9]+)\)/g;
        return markdownContent.replace(regex, (match, alt, src, width) => {
          let reg = /#\s*{\s*domainURL\s*}/g;
          src = src.replace(reg,domainUrl);
          return `<img src='${src}' alt='${alt}' width='${width}' />`;
        });
      };
      
      //替换domainURL
      const replaceDomainUrl = markdownContent => {
            const regex = /src\s*=\s*['"](.*?)['"]/g;
            return markdownContent.replace(regex, (match, src) => {
              // 检查是否是本地图片路径
              if (!src.startsWith("http")) {
                  let networkImageUrl = src;
                  // 绝对路径或相对路径处理
                  if(!src.startsWith("/")) {
                      networkImageUrl = "/"+src;
                  }
                  // 替换domainURL
                  return `src="${domainUrl}${networkImageUrl}"`;
              }
              return match;
            })
      }
      return {
        registerModal,
        hitTextDescData
      };
    },
  };
</script>

<style scoped lang="less">
  .pointer {
    cursor: pointer;
  }
  .header {
    font-size: 16px;
    font-weight: bold;
    margin-top: 10px;
  }
  .content {
    margin-top: 20px;
    max-height: 600px;
    overflow-y: auto;
    overflow-x: auto;
  }
  
  :deep(.v-md-editor-preview) {
    table {
      border-collapse: collapse;
      width: 100%;
      margin-bottom: 1rem;
      color: #333;
      display: table !important; /* 强制显示为表格 */
      
      th, td {
        border: 1px solid #dfe2e5;
        padding: 6px 13px;
      }
      
      th {
        font-weight: 600;
        background-color: #f8f8f8;
      }
      
      tr {
        background-color: #fff;
        border-top: 1px solid #c6cbd1;
      }

      tr:nth-child(2n) {
        background-color: #f8f8f8;
      }
    }
  }
  .title-tag {
    color: #477dee;
  }
</style>
