import type { ConfigProviderProps } from 'antd';
import { theme } from 'antd';
import { createStyles } from 'antd-style';
import { clsx } from 'clsx';
import { useMemo } from 'react';

const glassBorder = {
  boxShadow: [
    `0 8px 24px rgba(85, 85,85, 0.1)`,
    `inset 0 0 5px 2px rgba(255, 255, 255, 0.3)`,
    `inset 0 5px 2px rgba(255, 255, 255, 0.2)`,
  ].join(','),
};

const useStyles = createStyles((props) => {
  const { css, cssVar } = props;

  const glassBox = {
    ...glassBorder,
    background: `color-mix(in srgb, ${cssVar.colorBgContainer} 15%, transparent)`,
    backdropFilter: 'blur(12px)',
  };

  return {
    glassBorder,
    glassBox,
    notBackdropFilter: css({
      backdropFilter: 'none',
    }),
    app: css({
      textShadow: '0 1px rgba(0,0,0,0.1)',
    }),
    cardRoot: css({
      ...glassBox,
      backgroundColor: `color-mix(in srgb, ${cssVar.colorBgContainer} 40%, transparent)`,
    }),
    modalContainer: css({
      ...glassBox,
      backdropFilter: 'none',
    }),
    buttonRoot: css({
      ...glassBorder,
    }),
    buttonRootDefaultColor: css({
      background: 'transparent',
      color: cssVar.colorText,

      '&:hover': {
        background: 'rgba(255,255,255,0.2)',
        color: `color-mix(in srgb, ${cssVar.colorText} 90%, transparent)`,
      },

      '&:active': {
        background: 'rgba(255,255,255,0.1)',
        color: `color-mix(in srgb, ${cssVar.colorText} 80%, transparent)`,
      },
    }),
    buttonRootDangerColor: css({
      background: 'rgba(255, 120, 117, 0.1)',
      borderColor: 'rgba(255, 120, 117, 0.24)',
      color: cssVar.colorError,

      '&:hover': {
        background: 'rgba(255, 120, 117, 0.16)',
        borderColor: 'rgba(255, 120, 117, 0.32)',
        color: cssVar.colorErrorHover,
      },

      '&:active': {
        background: 'rgba(255, 120, 117, 0.12)',
        borderColor: 'rgba(255, 120, 117, 0.28)',
        color: cssVar.colorErrorActive,
      },
    }),

    dropdownRoot: css({
      ...glassBox,
      borderRadius: cssVar.borderRadiusLG,

      ul: {
        background: 'transparent',
      },
    }),
    notificationRoot: css({
      '&.ant-notification-notice, & .ant-notification-notice': {
        ...glassBox,
        background: `color-mix(in srgb, ${cssVar.colorBgContainer} 25%, transparent)`,
      },
    }),
    switchRoot: css({ ...glassBorder, border: 'none' }),
    segmentedRoot: css({
      ...glassBorder,
      background: 'transparent',
      backdropFilter: 'none',

      '& .ant-segmented-thumb': {
        ...glassBox,
      },

      '& .ant-segmented-item-selected': {
        ...glassBox,
      },
    }),
    // 菜单高亮:选中/hover 项在磨砂白侧边栏上加边框与阴影,增强可见度
    menuRoot: css({
      '& .ant-menu-item-selected, & .ant-menu-submenu-selected > .ant-menu-submenu-title':
        {
          boxShadow: `inset 0 0 0 1px ${cssVar.colorPrimaryBorder}, 0 2px 8px rgba(22, 119, 255, 0.15)`,
          fontWeight: 600,
        },
      '& .ant-menu-item:not(.ant-menu-item-selected):hover, & .ant-menu-submenu:not(.ant-menu-submenu-selected) > .ant-menu-submenu-title:hover':
        {
          boxShadow: `inset 0 0 0 1px ${cssVar.colorPrimary}`,
        },
    }),
    radioButtonRoot: css({
      '&.ant-radio-button-wrapper': {
        ...glassBorder,
        background: 'transparent',
        borderColor: 'rgba(255, 255, 255, 0.2)',
        color: cssVar.colorText,

        '&:hover': {
          borderColor: 'rgba(255, 255, 255, 0.24)',
          color: cssVar.colorText,
        },

        '&.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)':
          {
            ...glassBox,
            borderColor: 'rgba(255, 255, 255, 0.28)',
            color: cssVar.colorText,

            '&::before': {
              backgroundColor: 'rgba(255, 255, 255, 0.18)',
            },

            '&:hover': {
              color: cssVar.colorText,
            },
          },
      },
    }),
  };
});

const useGlassTheme = () => {
  const { styles } = useStyles();

  return useMemo<ConfigProviderProps>(
    () => ({
      theme: {
        algorithm: theme.defaultAlgorithm,
        token: {
          borderRadius: 12,
          borderRadiusLG: 12,
          borderRadiusSM: 12,
          borderRadiusXS: 12,
          motionDurationSlow: '0.2s',
          motionDurationMid: '0.1s',
          motionDurationFast: '0.05s',
          fontFamily: 'AlibabaSans, sans-serif',
        },
        components: {
          Button: {
            primaryShadow: 'none',
            dangerShadow: 'none',
            defaultShadow: 'none',
            colorError: '#ff7875',
            colorErrorHover: '#ffa39e',
            colorErrorActive: '#ff4d4f',
            colorErrorBg: 'rgba(255, 120, 117, 0.1)',
            colorErrorBgFilledHover: 'rgba(255, 120, 117, 0.16)',
            colorErrorBgActive: 'rgba(255, 120, 117, 0.12)',
            colorErrorBorder: 'rgba(255, 120, 117, 0.24)',
            colorErrorBorderHover: 'rgba(255, 120, 117, 0.32)',
            colorErrorText: '#ff7875',
            colorErrorTextHover: '#ffa39e',
            colorErrorTextActive: '#ff4d4f',
            defaultBg: 'rgba(255, 255, 255, 0.1)',
            defaultBorderColor: 'rgba(0, 0, 0, 0.15)',
            defaultHoverBg: 'rgba(255, 255, 255, 0.2)',
            defaultHoverBorderColor: 'rgba(0, 0, 0, 0.28)',
            defaultActiveBg: 'rgba(255, 255, 255, 0.1)',
            defaultActiveBorderColor: 'rgba(0, 0, 0, 0.28)',
          },
          Notification: {
            colorSuccessBg: 'rgba(183, 235, 143, 0.18)',
            colorErrorBg: 'rgba(255, 120, 117, 0.16)',
            colorInfoBg: 'rgba(145, 202, 255, 0.18)',
            colorWarningBg: 'rgba(255, 229, 143, 0.18)',
          },
          Layout: {
            bodyBg: 'rgba(255, 255, 255, 0.12)',
            footerBg: 'rgba(255, 255, 255, 0.12)',
            headerBg: 'rgba(255, 255, 255, 0.32)',
            headerColor: 'rgba(0, 0, 0, 0.88)',
            siderBg: 'rgba(255, 255, 255, 0.18)',
            triggerBg: 'rgba(255, 255, 255, 0.28)',
            triggerColor: 'rgba(0, 0, 0, 0.88)',
          },
          Menu: {
            activeBarBorderWidth: 0,
            groupTitleColor: 'rgba(0, 0, 0, 0.55)',
            // 提高不透明度：磨砂白侧边栏上,白色半透明高亮几乎不可见,需要更实的白 + 主色
            itemActiveBg: 'rgba(255, 255, 255, 0.55)',
            itemBg: 'transparent',
            itemColor: 'rgba(0, 0, 0, 0.78)',
            itemHoverBg: 'rgba(255, 255, 255, 0.7)',
            itemHoverColor: 'rgba(0, 0, 0, 0.88)',
            itemSelectedBg: 'rgba(255, 255, 255, 0.92)',
            itemSelectedColor: '#1677ff',
            subMenuItemBg: 'transparent',
            subMenuItemSelectedColor: '#1677ff',
          },
          Progress: {
            circleTextColor: 'rgba(0, 0, 0, 0.88)',
            defaultColor: '#1677ff',
            remainingColor: 'rgba(255, 255, 255, 0.28)',
          },
        },
      },
      app: {
        className: styles.app,
      },
      card: {
        classNames: {
          root: styles.cardRoot,
        },
      },
      modal: {
        classNames: {
          container: styles.modalContainer,
        },
      },
      button: {
        classNames: ({ props }) => ({
          root: clsx(
            styles.buttonRoot,
            props.color === 'default' && styles.buttonRootDefaultColor,
            props.color === 'danger' && styles.buttonRootDangerColor,
          ),
        }),
      },
      alert: {
        className: clsx(styles.glassBox, styles.notBackdropFilter),
      },
      colorPicker: {
        classNames: {
          root: clsx(styles.glassBox, styles.notBackdropFilter),
        },
        arrow: false,
      },
      dropdown: {
        classNames: {
          root: styles.dropdownRoot,
        },
      },
      select: {
        classNames: {
          root: clsx(styles.glassBox, styles.notBackdropFilter),
          popup: {
            root: styles.glassBox,
          },
        },
      },
      datePicker: {
        classNames: {
          root: clsx(styles.glassBox, styles.notBackdropFilter),
          popup: {
            container: styles.glassBox,
          },
        },
      },
      input: {
        classNames: {
          root: clsx(styles.glassBox, styles.notBackdropFilter),
        },
      },
      inputNumber: {
        classNames: {
          root: clsx(styles.glassBox, styles.notBackdropFilter),
        },
      },
      popover: {
        classNames: {
          container: styles.glassBox,
        },
      },
      notification: {
        classNames: {
          root: styles.notificationRoot,
        },
      },
      switch: {
        classNames: {
          root: styles.switchRoot,
        },
      },
      radio: {
        classNames: {
          root: styles.radioButtonRoot,
        },
      },
      segmented: {
        className: styles.segmentedRoot,
      },
      menu: {
        classNames: {
          root: styles.menuRoot,
        },
      },
      progress: {
        classNames: {
          track: styles.glassBorder,
        },
        styles: {
          track: {
            height: 12,
          },
          rail: {
            height: 12,
          },
        },
      },
      wave: {},
      checkbox: {},
      tooltip: {},
    }),
    [
      styles.app,
      styles.buttonRoot,
      styles.buttonRootDangerColor,
      styles.buttonRootDefaultColor,
      styles.cardRoot,
      styles.dropdownRoot,
      styles.glassBorder,
      styles.glassBox,
      styles.menuRoot,
      styles.modalContainer,
      styles.notBackdropFilter,
      styles.notificationRoot,
      styles.radioButtonRoot,
      styles.segmentedRoot,
      styles.switchRoot,
    ],
  );
};
export default useGlassTheme;
