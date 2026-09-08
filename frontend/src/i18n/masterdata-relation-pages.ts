export const masterdataRelationMessages = {
  'zh-CN': {
    productRelation: {
      customerTitle: '客户商品关系', supplierTitle: '供应商商品关系', customerOwner: '客户', supplierOwner: '供应商', entry: '商品关系',
      add: '新增关系', edit: '编辑', delete: '删除', save: '保存', cancel: '取消', close: '关闭', actions: '操作', refresh: '刷新',
      product: '商品', productPlaceholder: '选择商品', productUnavailable: '商品已停用或删除', productExhausted: '该往来单位已维护全部可选商品',
      customerProductCode: '客户商品编码', customerProductName: '客户商品名称', supplierProductCode: '供应商商品编码', supplierProductName: '供应商商品名称',
      codePlaceholder: '对方使用的编码', namePlaceholder: '对方使用的名称',
      deliveryPreference: '交付偏好', deliveryPreferencePlaceholder: '如：分批交付、指定承运商', packagingPreference: '包装偏好', packagingPreferencePlaceholder: '如：木箱、托盘',
      minPurchaseQty: '最小采购量', leadTimeDays: '采购提前期（天）', defaultSupplier: '默认供应商', remark: '备注', remarkPlaceholder: '请输入备注', status: '状态',
      empty: '暂无商品关系', yes: '是', no: '否', editorCreateTitle: '新增商品关系', editorEditTitle: '编辑商品关系',
      statusValue: { active: '启用', inactive: '停用' },
      validation: { product: '请选择商品' },
      message: {
        loadFailed: '加载商品关系失败', productsFailed: '加载商品列表失败', saved: '商品关系已保存', saveFailed: '保存商品关系失败',
        deleted: '商品关系已删除', deleteFailed: '删除商品关系失败', confirmDelete: '确认删除商品“{product}”的关系吗？', prompt: '确认'
      }
    }
  },
  'en-US': {
    productRelation: {
      customerTitle: 'Customer product relations', supplierTitle: 'Supplier product relations', customerOwner: 'Customer', supplierOwner: 'Supplier', entry: 'Product relations',
      add: 'Add relation', edit: 'Edit', delete: 'Delete', save: 'Save', cancel: 'Cancel', close: 'Close', actions: 'Actions', refresh: 'Refresh',
      product: 'Product', productPlaceholder: 'Select a product', productUnavailable: 'Product disabled or removed', productExhausted: 'Every selectable product is already mapped for this partner',
      customerProductCode: 'Customer product code', customerProductName: 'Customer product name', supplierProductCode: 'Supplier product code', supplierProductName: 'Supplier product name',
      codePlaceholder: 'Code used by the partner', namePlaceholder: 'Name used by the partner',
      deliveryPreference: 'Delivery preference', deliveryPreferencePlaceholder: 'e.g. split shipments, preferred carrier', packagingPreference: 'Packaging preference', packagingPreferencePlaceholder: 'e.g. wooden crate, pallet',
      minPurchaseQty: 'Minimum purchase qty', leadTimeDays: 'Lead time (days)', defaultSupplier: 'Default supplier', remark: 'Remark', remarkPlaceholder: 'Enter a remark', status: 'Status',
      empty: 'No product relations yet', yes: 'Yes', no: 'No', editorCreateTitle: 'New product relation', editorEditTitle: 'Edit product relation',
      statusValue: { active: 'Active', inactive: 'Inactive' },
      validation: { product: 'Select a product' },
      message: {
        loadFailed: 'Failed to load product relations', productsFailed: 'Failed to load products', saved: 'Product relation saved', saveFailed: 'Failed to save the product relation',
        deleted: 'Product relation deleted', deleteFailed: 'Failed to delete the product relation', confirmDelete: 'Delete the relation for product “{product}”?', prompt: 'Confirmation'
      }
    }
  }
} as const
