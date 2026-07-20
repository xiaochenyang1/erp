UPDATE sys_menu
SET menu_name = '会计期间',
    component = 'finance/periods/index',
    updated_by = 0
WHERE menu_code = 'FINANCE_PERIOD'
  AND deleted_flag = 0;
