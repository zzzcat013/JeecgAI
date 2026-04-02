<!--手动录入text-->
<template>
  <div class="p-2">
    <BasicModal destroyOnClose @register="registerModal" width="1000px" :title="title" @ok="handleOk" @cancel="handleCancel">
      <BasicForm @register="registerForm"></BasicForm>
    </BasicModal>
  </div>
</template>

<script lang="ts">
  import { ref, unref } from 'vue';
  import BasicModal from '@/components/Modal/src/BasicModal.vue';
  import { useModal, useModalInner } from '@/components/Modal';

  import BasicForm from '@/components/Form/src/BasicForm.vue';
  import { useForm } from '@/components/Form';
  import { docTextSchema } from '../api/AiKnowledgeBase.data';
  import { knowledgeSaveDoc, queryById } from '../api/AiKnowledgeBase.api';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { useGlobSetting } from "@/hooks/setting";

  const { domainUrl } = useGlobSetting();

  export default {
    name: 'AiragKnowledgeDocModal',
    components: {
      BasicForm,
      BasicModal,
    },
    emits: ['success', 'register'],
    setup(props, { emit }) {
      const title = ref<string>('创建知识库');

      //保存或修改
      const isUpdate = ref<boolean>(false);
      //知识库id
      const knowledgeId = ref<string>();
      //表单配置
      const [registerForm, { resetFields, setFieldsValue, validate, clearValidate, updateSchema }] = useForm({
        schemas: docTextSchema,
        showActionButtonGroup: false,
        layout: 'vertical',
        wrapperCol: { span: 24 },
      });


      //注册modal
      const [registerModal, { closeModal, setModalProps }] = useModalInner(async (data) => {
        //重置表单
        await resetFields();
        setModalProps({ confirmLoading: false });
        isUpdate.value = !!data?.isUpdate;
        title.value = isUpdate.value ? '编辑文档' : (data.type === 'file' ? '上传文档' : '创建文档');
        if (unref(isUpdate)) {
          if(data.record.type === 'file' && data.record.metadata){
            data.record.filePath = JSON.parse(data.record.metadata).filePath;
          }
          // 替换图片domainUrl以便预览
          if (data.record.content) {
            data.record.content = data.record.content.replace(/#\s*{\s*domainURL\s*}/g, domainUrl);
          }
          //表单赋值
          await setFieldsValue({
            ...data.record,
          });
        } else {
          knowledgeId.value = data.knowledgeId;
          await setFieldsValue({ type: data.type })
        }
        setModalProps({ bodyStyle: { padding: '10px' } });
      });

      /**
       * 保存
       */
      async function handleOk() {
        try {
          setModalProps({ confirmLoading: true });
          let values = await validate();
          if (!unref(isUpdate)) {
            values.knowledgeId = knowledgeId.value;
          }
          // 将 domainUrl 替换回 #{domainURL} 存储
          if (values.content) {
            // 使用 split/join 避免正则转义问题
            values.content = values.content.split(domainUrl).join('#{domainURL}');
          }
          if(values.filePath){
            values.metadata = JSON.stringify({ filePath: values.filePath });
            delete values.filePath;
          }
          await knowledgeSaveDoc(values);
          //关闭弹窗
          closeModal();
          //刷新列表
          emit('success');
        } finally {
          setModalProps({ confirmLoading: false });
        }
      }

      /**
       * 取消
       */
      function handleCancel() {
        closeModal();
      }

      return {
        registerModal,
        registerForm,
        title,
        handleOk,
        handleCancel,
      };
    },
  };
</script>

<style scoped lang="less">
  .pointer {
    cursor: pointer;
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
</style>
