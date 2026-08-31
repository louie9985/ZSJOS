import type { ConfigProviderProps } from 'antd';
import { theme } from 'antd';
import { useMemo } from 'react';

const useBlossomTheme = () => {
  return useMemo<ConfigProviderProps>(
    () => ({
      theme: {
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#ED4192',
          borderRadius: 16,
        },
        components: {
          Layout: {
            bodyBg: '#fdf6f9',
            footerBg: '#fdf6f9',
            headerBg: '#ffffff',
            headerColor: '#3f2330',
            siderBg: '#fffcfd',
            triggerBg: '#ffe4f0',
            triggerColor: '#ED4192',
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
            circleTextColor: '#3f2330',
            defaultColor: '#ED4192',
            remainingColor: 'rgba(237, 65, 146, 0.14)',
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

export default useBlossomTheme;
