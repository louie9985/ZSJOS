import type { ConfigProviderProps } from 'antd';
import { theme } from 'antd';
import { useMemo } from 'react';

const useV4Theme = () => {
  return useMemo<ConfigProviderProps>(
    () => ({
      theme: {
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1890ff',
          borderRadius: 2,
          colorBgLayout: '#f0f2f5',
        },
        components: {
          Layout: {
            bodyBg: '#f0f2f5',
            footerBg: '#f0f2f5',
            headerBg: '#001529',
            headerColor: '#ffffff',
            siderBg: '#ffffff',
            triggerBg: '#e6f4ff',
            triggerColor: '#000000d9',
          },
          Menu: {
            activeBarBorderWidth: 0,
            itemBg: 'transparent',
            subMenuItemBg: 'transparent',
          },
          Button: {
            defaultShadow: 'none',
            dangerShadow: 'none',
          },
          Alert: {},
          Modal: {},
          Card: {},
          Tooltip: {},
          Checkbox: {},
          Radio: {},
          Select: {},
          Input: {},
          Switch: {},
          Progress: {
            circleTextColor: '#000000d9',
            defaultColor: '#1890ff',
            remainingColor: '#f5f5f5',
          },
          Steps: {},
          Slider: {},
          ColorPicker: {},
          Notification: {},
        },
      },
      wave: {},
      app: {},
      card: {},
      modal: {},
      button: {},
      alert: {},
      colorPicker: {},
      checkbox: {},
      dropdown: {},
      select: {},
      datePicker: {},
      input: {},
      inputNumber: {},
      popover: {},
      tooltip: {},
      notification: {},
      switch: {},
      radio: {},
      segmented: {},
      progress: {},
    }),
    [],
  );
};

export default useV4Theme;
