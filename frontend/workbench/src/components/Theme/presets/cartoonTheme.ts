import type { ConfigProviderProps } from 'antd';
import { theme } from 'antd';
import { createStyles } from 'antd-style';
import { useMemo } from 'react';

const useStyles = createStyles(({ css, cssVar }) => {
  const sharedBorder = {
    border: `${cssVar.lineWidth} ${cssVar.lineType} ${cssVar.colorBorder}`,
  };

  return {
    sharedBorder,
    notificationRoot: css({
      '&.ant-notification-notice, & .ant-notification-notice': {
        ...sharedBorder,
        boxShadow: 'none',
      },
    }),
    progressTrack: css({
      ...sharedBorder,
      marginInlineStart: `calc(-1 * ${cssVar.lineWidth})`,
      marginBlockStart: `calc(-1 * ${cssVar.lineWidth})`,
    }),
  };
});

const useCartoonTheme = () => {
  const { styles } = useStyles();

  return useMemo<ConfigProviderProps>(
    () => ({
      theme: {
        algorithm: theme.defaultAlgorithm,
        token: {
          colorText: '#51463B',
          colorPrimary: '#225555',
          colorError: '#DA8787',
          colorInfo: '#9CD3D3',
          colorInfoBorder: '#225555',
          colorBorder: '#225555',
          colorBorderSecondary: '#88BBBB',
          lineWidth: 2,
          lineWidthBold: 2,
          borderRadius: 18,
          borderRadiusLG: 18,
          borderRadiusSM: 18,
          controlHeightSM: 28,
          controlHeight: 36,
          colorBgBase: '#FFF9F5',
          colorBgContainer: '#FFFAF5',
          fontFamily: 'AlibabaSans, sans-serif',
        },
        components: {
          Button: {
            primaryShadow: 'none',
            dangerShadow: 'none',
            defaultShadow: 'none',
          },
          Modal: {
            boxShadow: 'none',
          },
          Card: {
            colorBgContainer: '#FFF4E6',
          },
          Tooltip: {
            borderRadius: 6,
            colorBorder: '#225555',
            algorithm: true,
          },
          Select: {
            optionSelectedBg: '#E8DCC8',
          },
          Notification: {
            colorSuccessBg: '#E0EECF',
            colorErrorBg: '#F3D0C8',
            colorInfoBg: '#D9EEEE',
            colorWarningBg: '#FFF1B8',
          },
          Layout: {
            bodyBg: '#FFF9F5',
            footerBg: '#FFF9F5',
            headerBg: '#F6D878',
            headerColor: '#51463B',
            siderBg: '#FFF4E6',
            triggerBg: '#E8D29A',
            triggerColor: '#51463B',
          },
          Menu: {
            activeBarBorderWidth: 0,
            itemBg: 'transparent',
            subMenuItemBg: 'transparent',
          },
          Progress: {
            circleTextColor: '#51463B',
            defaultColor: '#225555',
            remainingColor: '#CBC4AF',
          },
        },
      },
      modal: {
        classNames: {
          container: styles.sharedBorder,
        },
      },
      colorPicker: {
        arrow: false,
      },
      popover: {
        classNames: {
          container: styles.sharedBorder,
        },
      },
      notification: {
        classNames: {
          root: styles.notificationRoot,
        },
      },
      progress: {
        classNames: {
          rail: styles.sharedBorder,
          track: styles.progressTrack,
        },
        styles: {
          rail: {
            height: 16,
          },
          track: {
            height: 16,
          },
        },
      },
      wave: {},
      app: {},
      card: {},
      button: {},
      alert: {},
      checkbox: {},
      dropdown: {},
      select: {},
      datePicker: {},
      input: {},
      inputNumber: {},
      tooltip: {},
      switch: {},
      radio: {},
      segmented: {},
    }),
    [styles.notificationRoot, styles.progressTrack, styles.sharedBorder],
  );
};

export default useCartoonTheme;
