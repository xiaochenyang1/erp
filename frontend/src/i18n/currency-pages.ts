export const currencyPageMessages = {
  'zh-CN': {
    currencyPages: { title: '币种与汇率', add: '新增汇率', from: '原币种', to: '目标币种', rate: '汇率', start: '生效日期', end: '失效日期', cancel: '取消', save: '保存', saved: '汇率已保存', failed: '保存汇率失败' },
    documentCurrency: {
      currency: '单据币种', rate: '汇率', base: '本位币：{currency}', refresh: '更新汇率', retry: '重试',
      hint: '汇率按订单日期读取。修改币种不会自动修改已有明细单价。',
      loadFailed: '无法读取账套币种或汇率，请重试', missingRate: '当前订单日期没有有效汇率，请先维护汇率',
      dateRequired: '请先选择订单日期', currencyRequired: '请选择单据币种', rateRequired: '请读取有效汇率',
      rateLoading: '正在读取汇率，请稍候', baseAmounts: '授信金额均以账套本位币计算'
    }
  },
  'en-US': {
    currencyPages: { title: 'Currencies & rates', add: 'Add rate', from: 'From', to: 'To', rate: 'Rate', start: 'Effective from', end: 'Effective to', cancel: 'Cancel', save: 'Save', saved: 'Exchange rate saved', failed: 'Failed to save exchange rate' },
    documentCurrency: {
      currency: 'Document currency', rate: 'Exchange rate', base: 'Base currency: {currency}', refresh: 'Update rate', retry: 'Retry',
      hint: 'Rates follow the order date. Changing currency does not change existing line prices.',
      loadFailed: 'Unable to load the account-book currency or rate. Please retry.', missingRate: 'No valid rate for this order date. Configure an exchange rate first.',
      dateRequired: 'Select the order date first', currencyRequired: 'Select a document currency', rateRequired: 'Load a valid exchange rate',
      rateLoading: 'Loading exchange rate. Please wait.', baseAmounts: 'Credit amounts are calculated in the account-book base currency'
    }
  }
} as const
