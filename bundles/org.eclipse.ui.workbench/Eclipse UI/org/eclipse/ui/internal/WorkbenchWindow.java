/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ui.internal;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.e4.core.contexts.ContextFunction;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IContextConstants;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.internal.workbench.Activator;
import org.eclipse.e4.ui.internal.workbench.Policy;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.commands.MCommand;
import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.advanced.MPerspective;
import org.eclipse.e4.ui.model.application.ui.advanced.MPerspectiveStack;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimBar;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimElement;
import org.eclipse.e4.ui.model.application.ui.basic.MTrimmedWindow;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.model.application.ui.basic.MWindowElement;
import org.eclipse.e4.ui.model.application.ui.basic.impl.BasicFactoryImpl;
import org.eclipse.e4.ui.model.application.ui.menu.ItemType;
import org.eclipse.e4.ui.model.application.ui.menu.MHandledMenuItem;
import org.eclipse.e4.ui.model.application.ui.menu.MHandledToolItem;
import org.eclipse.e4.ui.model.application.ui.menu.MMenu;
import org.eclipse.e4.ui.model.application.ui.menu.MMenuSeparator;
import org.eclipse.e4.ui.model.application.ui.menu.MToolBar;
import org.eclipse.e4.ui.model.application.ui.menu.MToolBarSeparator;
import org.eclipse.e4.ui.model.application.ui.menu.MToolControl;
import org.eclipse.e4.ui.model.application.ui.menu.impl.MenuFactoryImpl;
import org.eclipse.e4.ui.services.EContextService;
import org.eclipse.e4.ui.workbench.IPresentationEngine;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.ISaveHandler;
import org.eclipse.e4.ui.workbench.modeling.IWindowCloseHandler;
import org.eclipse.e4.ui.workbench.renderers.swt.TrimmedPartLayout;
import org.eclipse.jface.action.AbstractGroupMarker;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ContributionManager;
import org.eclipse.jface.action.CoolBarManager;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.ICoolBarManager;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.internal.provisional.action.CoolBarManager2;
import org.eclipse.jface.internal.provisional.action.IToolBarContributionItem;
import org.eclipse.jface.internal.provisional.action.IToolBarManager2;
import org.eclipse.jface.internal.provisional.action.ToolBarManager2;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.operation.ModalContext;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.osgi.util.TextProcessor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ActiveShellExpression;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.eclipse.ui.commands.ICommandImageService;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.internal.StartupThreading.StartupRunnable;
import org.eclipse.ui.internal.actions.CommandAction;
import org.eclipse.ui.internal.e4.compatibility.CompatibilityEditor;
import org.eclipse.ui.internal.e4.compatibility.E4Util;
import org.eclipse.ui.internal.e4.compatibility.ModeledPageLayout;
import org.eclipse.ui.internal.e4.compatibility.SelectionService;
import org.eclipse.ui.internal.expressions.WorkbenchWindowExpression;
import org.eclipse.ui.internal.handlers.ActionCommandMappingService;
import org.eclipse.ui.internal.handlers.ActionDelegateHandlerProxy;
import org.eclipse.ui.internal.handlers.IActionCommandMappingService;
import org.eclipse.ui.internal.handlers.LegacyHandlerService;
import org.eclipse.ui.internal.layout.ITrimManager;
import org.eclipse.ui.internal.menus.IActionSetsListener;
import org.eclipse.ui.internal.menus.LegacyActionPersistence;
import org.eclipse.ui.internal.menus.WorkbenchMenuService;
import org.eclipse.ui.internal.misc.UIListenerLogging;
import org.eclipse.ui.internal.progress.ProgressRegion;
import org.eclipse.ui.internal.provisional.application.IActionBarConfigurer2;
import org.eclipse.ui.internal.provisional.presentations.IActionBarPresentationFactory;
import org.eclipse.ui.internal.registry.IWorkbenchRegistryConstants;
import org.eclipse.ui.internal.registry.UIExtensionTracker;
import org.eclipse.ui.internal.services.EvaluationReference;
import org.eclipse.ui.internal.services.IServiceLocatorCreator;
import org.eclipse.ui.internal.services.IWorkbenchLocationService;
import org.eclipse.ui.internal.services.ServiceLocator;
import org.eclipse.ui.internal.services.WorkbenchLocationService;
import org.eclipse.ui.internal.util.PrefUtil;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.IMenuService;
import org.eclipse.ui.menus.MenuUtil;
import org.eclipse.ui.presentations.AbstractPresentationFactory;
import org.eclipse.ui.services.IDisposable;
import org.eclipse.ui.services.IEvaluationService;
import org.eclipse.ui.services.IServiceScopes;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

/**
 * A window within the workbench.
 */
public class WorkbenchWindow implements IWorkbenchWindow {

	public static final String ACTION_SET_CMD_PREFIX = "AS::"; //$NON-NLS-1$

	@Inject
	private IWorkbench workbench;
	@Inject
	private MTrimmedWindow model;
	@Inject
	private IPresentationEngine engine;

	@Inject
	private MApplication application;

	@Inject
	EModelService modelService;

	@Inject
	private IEventBroker eventBroker;

	private WorkbenchPage page;

	private WorkbenchWindowAdvisor windowAdvisor;

	private ActionBarAdvisor actionBarAdvisor;

	private int number;

	private PageListenerList pageListeners = new PageListenerList();

	private PerspectiveListenerList perspectiveListeners = new PerspectiveListenerList();

	private WWinActionBars actionBars;

	private boolean updateDisabled = true;

	private boolean closing = false;

	private boolean shellActivated = false;

	ProgressRegion progressRegion = null;

	private List<MTrimElement> workbenchTrimElements = new ArrayList<MTrimElement>();

	private HeapStatus heapStatus;

	/**
	 * The map of services maintained by the workbench window. These services
	 * are initialized during workbench window during the
	 * {@link #configureShell(Shell)}.
	 */
	private ServiceLocator serviceLocator;


	/**
	 * Bit flags indication which submenus (New, Show Views, ...) this window
	 * contains. Initially none.
	 * 
	 * @since 3.0
	 */
	private int submenus = 0x00;

	/**
	 * Object for configuring this workbench window. Lazily initialized to an
	 * instance unique to this window.
	 * 
	 * @since 3.0
	 */
	private WorkbenchWindowConfigurer windowConfigurer = null;

	/**
	 * A flag used for indicating whether the workbench is in the process of
	 * shutting down for a restart or not. See bug 312821.
	 */
	private boolean restarting = false;

	/**
	 * List of generic property listeners.
	 * 
	 * @since 3.3
	 */
	private ListenerList genericPropertyListeners = new ListenerList();

	private IAdaptable input;

	private IPerspectiveDescriptor perspective;

	private EventHandler windowWidgetHandler = new EventHandler() {
		public void handleEvent(Event event) {
			if (event.getProperty(UIEvents.EventTags.ELEMENT) == model
					&& event.getProperty(UIEvents.EventTags.NEW_VALUE) == null) {
				removeTrimContributions();
				MMenu menu = model.getMainMenu();
				if (menu != null) {
					engine.removeGui(menu);
					model.setMainMenu(null);
				}

				eventBroker.unsubscribe(windowWidgetHandler);
			}
		}
	};

	static final String TEXT_DELIMITERS = TextProcessor.getDefaultDelimiters() + "-"; //$NON-NLS-1$

	// constants for shortcut bar group ids
	static final String GRP_PAGES = "pages"; //$NON-NLS-1$

	static final String GRP_PERSPECTIVES = "perspectives"; //$NON-NLS-1$

	static final String GRP_FAST_VIEWS = "fastViews"; //$NON-NLS-1$

	// static fields for inner classes.
	static final int VGAP = 0;

	static final int CLIENT_INSET = 3;

	static final int BAR_SIZE = 23;

	/**
	 * Coolbar visibility change property.
	 * 
	 * @since 3.3
	 */
	public static final String PROP_COOLBAR_VISIBLE = "coolbarVisible"; //$NON-NLS-1$

	/**
	 * Perspective bar visibility change property.
	 * 
	 * @since 3.3
	 */
	public static final String PROP_PERSPECTIVEBAR_VISIBLE = "perspectiveBarVisible"; //$NON-NLS-1$

	/**
	 * The status line visibility change property. for internal use only.
	 * 
	 * @since 3.4
	 */
	public static final String PROP_STATUS_LINE_VISIBLE = "statusLineVisible"; //$NON-NLS-1$

	/**
	 * Constant (bit mask) indicating which the Show View submenu is probably
	 * present somewhere in this window.
	 * 
	 * @see #addSubmenu
	 * @since 3.0
	 */
	public static final int SHOW_VIEW_SUBMENU = 0x01;

	/**
	 * Constant (bit mask) indicating which the Open Perspective submenu is
	 * probably present somewhere in this window.
	 * 
	 * @see #addSubmenu
	 * @since 3.0
	 */
	public static final int OPEN_PERSPECTIVE_SUBMENU = 0x02;

	/**
	 * Constant (bit mask) indicating which the New Wizard submenu is probably
	 * present somewhere in this window.
	 * 
	 * @see #addSubmenu
	 * @since 3.0
	 */
	public static final int NEW_WIZARD_SUBMENU = 0x04;

	/**
	 * Remembers that this window contains the given submenu.
	 * 
	 * @param type
	 *            the type of submenu, one of: {@link #NEW_WIZARD_SUBMENU
	 *            NEW_WIZARD_SUBMENU}, {@link #OPEN_PERSPECTIVE_SUBMENU
	 *            OPEN_PERSPECTIVE_SUBMENU}, {@link #SHOW_VIEW_SUBMENU
	 *            SHOW_VIEW_SUBMENU}
	 * @see #containsSubmenu
	 * @since 3.0
	 */
	public void addSubmenu(int type) {
		submenus |= type;
	}

	/**
	 * Checks to see if this window contains the given type of submenu.
	 * 
	 * @param type
	 *            the type of submenu, one of: {@link #NEW_WIZARD_SUBMENU
	 *            NEW_WIZARD_SUBMENU}, {@link #OPEN_PERSPECTIVE_SUBMENU
	 *            OPEN_PERSPECTIVE_SUBMENU}, {@link #SHOW_VIEW_SUBMENU
	 *            SHOW_VIEW_SUBMENU}
	 * @return <code>true</code> if window contains submenu, <code>false</code>
	 *         otherwise
	 * @see #addSubmenu
	 * @since 3.0
	 */
	public boolean containsSubmenu(int type) {
		return ((submenus & type) != 0);
	}

	/**
	 * Constant indicating that all the actions bars should be filled.
	 * 
	 * @since 3.0
	 */
	private static final int FILL_ALL_ACTION_BARS = ActionBarAdvisor.FILL_MENU_BAR
			| ActionBarAdvisor.FILL_COOL_BAR | ActionBarAdvisor.FILL_STATUS_LINE;

	/**
	 * Creates and initializes a new workbench window.
	 * 
	 * @param number
	 *            the number for the window
	 */
	public WorkbenchWindow(IAdaptable input, IPerspectiveDescriptor pers) {
		this.input = input;
		perspective = pers;
	}

	@PostConstruct
	public void setup() {
		IEclipseContext windowContext = model.getContext();
		IServiceLocatorCreator slc = (IServiceLocatorCreator) workbench
				.getService(IServiceLocatorCreator.class);
		this.serviceLocator = (ServiceLocator) slc.createServiceLocator(workbench, null,
				new IDisposable() {
					public void dispose() {
						final Shell shell = getShell();
						if (shell != null && !shell.isDisposed()) {
							close();
						}
					}
				});
		serviceLocator.setContext(windowContext);

		windowContext.set(IExtensionTracker.class.getName(), new ContextFunction() {

			@Override
			public Object compute(IEclipseContext context) {
				if (tracker == null) {
					tracker = new UIExtensionTracker(getWorkbench().getDisplay());
				}
				return tracker;
			}
		});

		windowContext.set(IWindowCloseHandler.class.getName(), new IWindowCloseHandler() {
			public boolean close(MWindow window) {
				if (restarting) {
					return WorkbenchWindow.this.close();
				}
				return getWindowAdvisor().preWindowShellClose() && WorkbenchWindow.this.close();
			}
		});

		final ISaveHandler defaultSaveHandler = windowContext.get(ISaveHandler.class);
		windowContext.set(ISaveHandler.class, new ISaveHandler() {
			public Save promptToSave(MPart dirtyPart) {
				Object object = dirtyPart.getObject();
				if (object instanceof CompatibilityEditor) {
					IEditorPart editor = ((CompatibilityEditor) object).getEditor();
					return SaveableHelper.savePart(editor, editor, WorkbenchWindow.this, true) ? Save.NO
							: Save.CANCEL;
				}
				return defaultSaveHandler.promptToSave(dirtyPart);
			}

			public Save[] promptToSave(Collection<MPart> dirtyParts) {
				return defaultSaveHandler.promptToSave(dirtyParts);
			}
		});

		try {
			page = new WorkbenchPage(this, input);
		} catch (WorkbenchException e) {
			WorkbenchPlugin.log(e);
		}

		windowContext.set(IWorkbenchWindow.class.getName(), this);
		windowContext.set(IWorkbenchPage.class.getName(), page);

		windowContext.set(ISources.ACTIVE_WORKBENCH_WINDOW_NAME, this);
		windowContext.set(ISources.ACTIVE_WORKBENCH_WINDOW_SHELL_NAME, getShell());
		EContextService cs = (EContextService) windowContext.get(EContextService.class.getName());
		cs.activateContext(IContextService.CONTEXT_ID_WINDOW);
		cs.getActiveContextIds();

		initializeDefaultServices();

		ContextInjectionFactory.inject(page, model.getContext());

		// Fill the action bars
		fillActionBars(FILL_ALL_ACTION_BARS);

		List<MPerspectiveStack> ps = modelService.findElements(model, null,
				MPerspectiveStack.class, null);
		MPerspective curPersp = null;
		if (ps.size() > 0) {
			MPerspectiveStack stack = ps.get(0);
			if (stack.getSelectedElement() != null) {
				curPersp = stack.getSelectedElement();
				IPerspectiveDescriptor thePersp = getWorkbench().getPerspectiveRegistry()
						.findPerspectiveWithId(curPersp.getElementId());
				if (thePersp != null) {
					perspective = thePersp;
				}
			}
		}
		page.setPerspective(perspective);

		if (curPersp != null) {
			populateTrimContributions(ModeledPageLayout.getIds(curPersp, ACTION_SET_CMD_PREFIX),
					true);
		}

		// register with the tracker

		fireWindowOpening();

		Shell shell = (Shell) model.getWidget();
		MMenu mainMenu = model.getMainMenu();
		if (mainMenu == null) {
			mainMenu = MenuFactoryImpl.eINSTANCE.createMenu();
			mainMenu.setElementId("org.eclipse.ui.main.menu"); //$NON-NLS-1$

			fill(mainMenu, menuManager);
			model.setMainMenu(mainMenu);
			Menu menu = (Menu) engine.createGui(mainMenu, model.getWidget());
			shell.setMenuBar(menu);
		}

		createProgressIndicator(shell);
		createHeapStatus(shell);

		eventBroker.subscribe(
				UIEvents.buildTopic(UIEvents.UIElement.TOPIC, UIEvents.UIElement.WIDGET),
				windowWidgetHandler);
	}

	private void removeTrimContributions() {
		MTrimBar trimBar = getTopTrim();
		for (MTrimElement trimElement : workbenchTrimElements) {
			trimElement.setToBeRendered(false);
			trimBar.getChildren().remove(trimElement);
		}
		workbenchTrimElements.clear();
	}

	void populateTrimContributions(List<String> actionSets, boolean reload) {
		removeTrimContributions();

		MTrimBar trimBar = getTopTrim();
		fill(trimBar, getCoolBarManager2());

		if (false) {
			// TODO don't do this yet, it should be handled by the TrimRenderer
			for (String actionSet : actionSets) {
				MToolBar toolBar = MenuFactoryImpl.eINSTANCE.createToolBar();
				toolBar.setElementId(actionSet);
				MToolBarSeparator separator = MenuFactoryImpl.eINSTANCE.createToolBarSeparator();
				separator.setElementId(actionSet);
				separator.setToBeRendered(false);
				toolBar.getChildren().add(separator);
				trimBar.getChildren().add(toolBar);
				workbenchTrimElements.add(toolBar);
			}
		}

		// TODO why aren't these added as trim contributions
		// that would remove everything from this method except the fill(*)
		MToolControl spacerControl = MenuFactoryImpl.eINSTANCE.createToolControl();
		spacerControl.setElementId("PerspectiveSpacer"); //$NON-NLS-1$
		spacerControl
				.setContributionURI("platform:/plugin/org.eclipse.e4.ui.workbench.addons.swt/org.eclipse.e4.ui.workbench.addons.perspectiveswitcher.SpacerToolControl"); //$NON-NLS-1$

		MToolControl switcherControl = MenuFactoryImpl.eINSTANCE.createToolControl();
		switcherControl.setElementId("PerspectiveSwitcher"); //$NON-NLS-1$
		switcherControl
				.setContributionURI("platform:/plugin/org.eclipse.e4.ui.workbench.addons.swt/org.eclipse.e4.ui.workbench.addons.perspectiveswitcher.PerspectiveSwitcher"); //$NON-NLS-1$

		MToolControl searchControl = MenuFactoryImpl.eINSTANCE.createToolControl();
		searchControl.setElementId("SearchField"); //$NON-NLS-1$
		searchControl
				.setContributionURI("platform:/plugin/org.eclipse.ui.workbench/org.eclipse.ui.internal.quickaccess.SearchField"); //$NON-NLS-1$

		trimBar.getChildren().add(spacerControl);
		trimBar.getChildren().add(searchControl);
		trimBar.getChildren().add(switcherControl);
		trimBar.setToBeRendered(true);

		workbenchTrimElements.add(spacerControl);
		workbenchTrimElements.add(searchControl);
		workbenchTrimElements.add(switcherControl);
	}

	private MTrimBar getTopTrim() {
		List<MTrimBar> trimBars = model.getTrimBars();
		if (trimBars.size() == 0) {
			MTrimBar trimBar = BasicFactoryImpl.eINSTANCE.createTrimBar();
			trimBar.setElementId("org.eclipse.ui.main.toolbar"); //$NON-NLS-1$
			trimBars.add(trimBar);
			return trimBar;
		}
		return trimBars.get(0);
	}

	private void fill(MMenu menu, IMenuManager manager) {
		for (IContributionItem item : manager.getItems()) {
			if (item instanceof MenuManager) {
				MMenu subMenu = MenuFactoryImpl.eINSTANCE.createMenu();
				MenuManager menuManager = (MenuManager) item;
				subMenu.setLabel(menuManager.getMenuText());
				subMenu.setElementId(menuManager.getId());
				fill(subMenu, menuManager);
				menu.getChildren().add(subMenu);
			} else if (item instanceof CommandContributionItem) {
				String id = ((CommandContributionItem) item).getCommand().getId();
				for (MCommand command : application.getCommands()) {
					if (id.equals(command.getElementId())) {
						MHandledMenuItem menuItem = MenuFactoryImpl.eINSTANCE
								.createHandledMenuItem();
						menuItem.setCommand(command);
						menuItem.setLabel(command.getCommandName());
						menuItem.setIconURI(getIconURI(id));
						menu.getChildren().add(menuItem);
						break;
					}
				}
			} else if (item instanceof ActionContributionItem) {
				IAction action = ((ActionContributionItem) item).getAction();
				String id = action.getActionDefinitionId();
				if (action instanceof OpenPreferencesAction) {
					for (MCommand command : application.getCommands()) {
						if (IWorkbenchCommandConstants.WINDOW_PREFERENCES.equals(command
								.getElementId())) {
							MHandledMenuItem menuItem = MenuFactoryImpl.eINSTANCE
									.createHandledMenuItem();
							menuItem.setCommand(command);
							menuItem.setLabel(command.getCommandName());
							menuItem.setIconURI(getIconURI(action.getImageDescriptor()));

							switch (action.getStyle()) {
							case IAction.AS_CHECK_BOX:
								menuItem.setType(ItemType.CHECK);
								break;
							case IAction.AS_RADIO_BUTTON:
								menuItem.setType(ItemType.RADIO);
								break;
							default:
								menuItem.setType(ItemType.PUSH);
								break;
							}

							menu.getChildren().add(menuItem);
							break;
						}
					}
				} else if (id != null) {
					for (MCommand command : application.getCommands()) {
						if (id.equals(command.getElementId())) {
							MHandledMenuItem menuItem = MenuFactoryImpl.eINSTANCE
									.createHandledMenuItem();
							menuItem.setCommand(command);
							menuItem.setLabel(command.getCommandName());
							menuItem.setIconURI(getIconURI(action.getImageDescriptor()));

							switch (action.getStyle()) {
							case IAction.AS_CHECK_BOX:
								menuItem.setType(ItemType.CHECK);
								break;
							case IAction.AS_RADIO_BUTTON:
								menuItem.setType(ItemType.RADIO);
								break;
							default:
								menuItem.setType(ItemType.PUSH);
								break;
							}

							menu.getChildren().add(menuItem);
							break;
						}
					}
				}
			} else if (item instanceof AbstractGroupMarker) {
				MMenuSeparator separator = MenuFactoryImpl.eINSTANCE.createMenuSeparator();
				separator.setToBeRendered(item.isVisible());
				separator.setElementId(item.getId());
				menu.getChildren().add(separator);
			}
		}
	}

	private void fill(MTrimBar container, IContributionManager manager) {
		for (IContributionItem item : manager.getItems()) {
			if (item instanceof IToolBarContributionItem) {
				IToolBarManager manager2 = ((IToolBarContributionItem) item).getToolBarManager();
				MToolBar toolBar = MenuFactoryImpl.eINSTANCE.createToolBar();
				toolBar.setElementId(item.getId());
				fill(toolBar, manager2);
				container.getChildren().add(toolBar);
				workbenchTrimElements.add(toolBar);
			} else if (item instanceof IContributionManager) {
				MToolBar toolBar = MenuFactoryImpl.eINSTANCE.createToolBar();
				toolBar.setElementId(item.getId());
				fill(toolBar, (IContributionManager) item);
				container.getChildren().add(toolBar);
				workbenchTrimElements.add(toolBar);
			} else if (item instanceof AbstractGroupMarker) {
				MToolBarSeparator separator = MenuFactoryImpl.eINSTANCE.createToolBarSeparator();
				separator.setToBeRendered(item.isVisible());
				separator.setElementId(item.getId());

				MToolBar toolBar = MenuFactoryImpl.eINSTANCE.createToolBar();
				toolBar.setElementId(item.getId());
				toolBar.getChildren().add(separator);
				container.getChildren().add(toolBar);
				workbenchTrimElements.add(toolBar);
			}
		}
	}

	private void fill(MToolBar container, IContributionManager manager) {
		for (IContributionItem item : manager.getItems()) {
			if (item instanceof IToolBarContributionItem) {
				IToolBarManager manager2 = ((IToolBarContributionItem) item).getToolBarManager();
				fill(container, manager2);
			} else if (item instanceof IContributionManager) {
				fill(container, (IContributionManager) item);
			} else if (item instanceof CommandContributionItem) {
				String id = ((CommandContributionItem) item).getCommand().getId();
				for (MCommand command : application.getCommands()) {
					if (id.equals(command.getElementId())) {
						MHandledToolItem menuItem = MenuFactoryImpl.eINSTANCE
								.createHandledToolItem();
						menuItem.setCommand(command);

						String iconURI = getIconURI(id);
						if (iconURI == null) {
							menuItem.setLabel(command.getCommandName());
						} else {
							menuItem.setIconURI(iconURI);
						}

						container.getChildren().add(menuItem);
						break;
					}
				}
			} else if (item instanceof AbstractGroupMarker) {
				MToolBarSeparator separator = MenuFactoryImpl.eINSTANCE.createToolBarSeparator();
				separator.setToBeRendered(item.isVisible());
				separator.setElementId(item.getId());
				container.getChildren().add(separator);
			} else if (item instanceof ActionContributionItem) {
				IAction action = ((ActionContributionItem) item).getAction();
				String id = action.getActionDefinitionId();
				if (id != null) {
					for (MCommand command : application.getCommands()) {
						if (id.equals(command.getElementId())) {
							MHandledToolItem toolItem = MenuFactoryImpl.eINSTANCE
									.createHandledToolItem();
							toolItem.setCommand(command);
							container.getChildren().add(toolItem);

							String iconURI = getIconURI(action.getImageDescriptor());
							if (iconURI == null) {
								iconURI = getIconURI(id);
								if (iconURI == null) {
									toolItem.setLabel(command.getCommandName());
								} else {
									toolItem.setIconURI(iconURI);
								}
							} else {
								toolItem.setIconURI(iconURI);
							}

							switch (action.getStyle()) {
							case IAction.AS_CHECK_BOX:
								toolItem.setType(ItemType.CHECK);
								break;
							case IAction.AS_RADIO_BUTTON:
								toolItem.setType(ItemType.RADIO);
								break;
							default:
								toolItem.setType(ItemType.PUSH);
								break;
							}
							break;
						}
					}
				}
			}
		}
	}

	private String getIconURI(ImageDescriptor descriptor) {
		if (descriptor == null) {
			return null;
		}

		String string = descriptor.toString();
		if (string.startsWith("URLImageDescriptor(")) { //$NON-NLS-1$
			string = string.substring("URLImageDescriptor(".length()); //$NON-NLS-1$
			string = string.substring(0, string.length() - 1);

			BundleContext ctxt = FrameworkUtil.getBundle(WorkbenchWindow.class).getBundleContext();

			try {
				URI uri = new URI(string);
				String host = uri.getHost();
				String bundleId = host.substring(0, host.indexOf('.'));
				Bundle bundle = ctxt.getBundle(Long.parseLong(bundleId));
				StringBuilder builder = new StringBuilder("platform:/plugin/"); //$NON-NLS-1$
				builder.append(bundle.getSymbolicName());
				builder.append(uri.getPath());
				return builder.toString();
			} catch (URISyntaxException e) {
				// ignored
			}
		}
		return null;
	}

	private String getIconURI(String commandId) {
		ICommandImageService imageService = (ICommandImageService) workbench
				.getService(ICommandImageService.class);
		ImageDescriptor descriptor = imageService.getImageDescriptor(commandId);
		return getIconURI(descriptor);
	}

	public static String getId(IConfigurationElement element) {
		String id = element.getAttribute(IWorkbenchRegistryConstants.ATT_ID);

		// For sub-menu management -all- items must be id'd so enforce this
		// here (we could optimize by checking the 'name' of the config
		// element == "menu"
		if (id == null || id.length() == 0) {
			id = getCommandId(element);
		}
		if (id == null || id.length() == 0) {
			id = element.toString();
		}

		return id;
	}

	public static String getCommandId(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_COMMAND_ID);
	}

	public static String getActionSetCommandId(IConfigurationElement element) {
		String id = getDefinitionId(element);
		if (id != null) {
			return id;
		}
		id = getId(element);
		String actionSetId = null;
		Object obj = element.getParent();
		while (obj instanceof IConfigurationElement && actionSetId == null) {
			IConfigurationElement parent = (IConfigurationElement) obj;
			if (parent.getName().equals(IWorkbenchRegistryConstants.TAG_ACTION_SET)) {
				actionSetId = getId(parent);
			}
			obj = parent.getParent();
		}
		return ACTION_SET_CMD_PREFIX + actionSetId + '/' + id;
	}

	public static String getDefinitionId(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_DEFINITION_ID);
	}

	public static boolean getRetarget(IConfigurationElement element) {
		String r = element.getAttribute(IWorkbenchRegistryConstants.ATT_RETARGET);
		return Boolean.valueOf(r);
	}

	private void readActionSets() {
		IEclipseContext windowContext = model.getContext();
		WorkbenchWindowExpression windowExpression = new WorkbenchWindowExpression(this);
		ICommandService cs = (ICommandService) windowContext.get(ICommandService.class.getName());
		IExtensionRegistry registry = (IExtensionRegistry) windowContext
				.get(IExtensionRegistry.class.getName());
		IExtensionPoint extPoint = registry
				.getExtensionPoint(IWorkbenchRegistryConstants.EXTENSION_ACTION_SETS);
		IConfigurationElement[] actionSetElements = extPoint.getConfigurationElements();
		for (IConfigurationElement ase : actionSetElements) {
			IConfigurationElement[] elements = ase
					.getChildren(IWorkbenchRegistryConstants.TAG_ACTION);
			for (IConfigurationElement configElement : elements) {
				String id = getId(configElement);
				String cmdId = getActionSetCommandId(configElement);
				if (id == null || id.length() == 0 || getRetarget(configElement)) {
					continue;
				}
				Command cmd = cs.getCommand(cmdId);
				if (!cmd.isDefined()) {
					Activator.trace(Policy.DEBUG_CMDS, "Still no command for " //$NON-NLS-1$
							+ cmdId, null);
					continue;
				}
				LegacyHandlerService.registerLegacyHandler(windowContext, id, cmdId,
						new ActionDelegateHandlerProxy(configElement,
								IWorkbenchRegistryConstants.ATT_CLASS, id,
								new ParameterizedCommand(cmd, null), this, null, null, null),
						windowExpression);
			}
		}
	}

	/**
	 * Return the style bits for the shortcut bar.
	 * 
	 * @return int
	 */
	protected int perspectiveBarStyle() {
		return SWT.FLAT | SWT.WRAP | SWT.RIGHT | SWT.HORIZONTAL;
	}


	private boolean coolBarVisible = true;

	private boolean perspectiveBarVisible = true;

	private boolean fastViewBarVisible = true;

	private boolean statusLineVisible = true;


	/**
	 * The handlers for global actions that were last submitted to the workbench
	 * command support. This is a map of command identifiers to
	 * <code>ActionHandler</code>. This map is never <code>null</code>, and is
	 * never empty as long as at least one global action has been registered.
	 */
	private Map globalActionHandlersByCommandId = new HashMap();

	/**
	 * The list of handler submissions submitted to the workbench command
	 * support. This list may be empty, but it is never <code>null</code>.
	 */
	private List handlerActivations = new ArrayList();

	/**
	 * The number of large updates that are currently going on. If this is
	 * number is greater than zero, then UI updateActionBars is a no-op.
	 * 
	 * @since 3.1
	 */
	private int largeUpdates = 0;

	private IExtensionTracker tracker;

	void registerGlobalAction(IAction globalAction) {
		String commandId = globalAction.getActionDefinitionId();

		if (commandId != null) {
			final Object value = globalActionHandlersByCommandId.get(commandId);
			if (value instanceof ActionHandler) {
				// This handler is about to get clobbered, so dispose it.
				final ActionHandler handler = (ActionHandler) value;
				handler.dispose();
			}

			if (globalAction instanceof CommandAction) {
				final String actionId = globalAction.getId();
				if (actionId != null) {
					final IActionCommandMappingService mappingService = (IActionCommandMappingService) serviceLocator
							.getService(IActionCommandMappingService.class);
					mappingService.map(actionId, commandId);
				}
			} else {
				globalActionHandlersByCommandId.put(commandId, new ActionHandler(globalAction));
			}
		}

		submitGlobalActions();
	}

	/**
	 * <p>
	 * Submits the action handlers for action set actions and global actions.
	 * Global actions are given priority, so that if a global action and an
	 * action set action both handle the same command, the global action is
	 * given priority.
	 * </p>
	 * <p>
	 * These submissions are submitted as <code>Priority.LEGACY</code>, which
	 * means that they are the lowest priority. This means that if a higher
	 * priority submission handles the same command under the same conditions,
	 * that that submission will become the handler.
	 * </p>
	 */
	void submitGlobalActions() {
		final IHandlerService handlerService = (IHandlerService) getService(
				IHandlerService.class);

		/*
		 * Mash the action sets and global actions together, with global actions
		 * taking priority.
		 */
		Map handlersByCommandId = new HashMap();
		handlersByCommandId.putAll(globalActionHandlersByCommandId);

		List newHandlers = new ArrayList(handlersByCommandId.size());

		Iterator existingIter = handlerActivations.iterator();
		while (existingIter.hasNext()) {
			IHandlerActivation next = (IHandlerActivation) existingIter.next();

			String cmdId = next.getCommandId();

			Object handler = handlersByCommandId.get(cmdId);
			if (handler == next.getHandler()) {
				handlersByCommandId.remove(cmdId);
				newHandlers.add(next);
			} else {
				handlerService.deactivateHandler(next);
			}
		}

		final Shell shell = getShell();
		if (shell != null) {
			final Expression expression = new ActiveShellExpression(shell);
			for (Iterator iterator = handlersByCommandId.entrySet().iterator(); iterator.hasNext();) {
				Map.Entry entry = (Map.Entry) iterator.next();
				String commandId = (String) entry.getKey();
				IHandler handler = (IHandler) entry.getValue();
				newHandlers.add(handlerService.activateHandler(commandId, handler, expression));
			}
		}

		handlerActivations = newHandlers;
	}

	/**
	 * Add a generic property listener.
	 * 
	 * @param listener
	 *            the listener to add
	 * @since 3.3
	 */
	public void addPropertyChangeListener(IPropertyChangeListener listener) {
		genericPropertyListeners.add(listener);
	}

	/**
	 * Removes a generic property listener.
	 * 
	 * @param listener
	 *            the listener to remove
	 * @since 3.3
	 */
	public void removePropertyChangeListener(IPropertyChangeListener listener) {
		genericPropertyListeners.remove(listener);
	}

	private void firePropertyChanged(final String property, final Object oldValue,
			final Object newValue) {
		PropertyChangeEvent event = new PropertyChangeEvent(this, property, oldValue, newValue);
		Object[] listeners = genericPropertyListeners.getListeners();
		for (int i = 0; i < listeners.length; i++) {
			IPropertyChangeListener listener = (IPropertyChangeListener) listeners[i];
			listener.propertyChange(event);
		}
	}

	/*
	 * Adds an listener to the part service.
	 */
	public void addPageListener(IPageListener l) {
		pageListeners.addPageListener(l);
	}

	/**
	 * @see org.eclipse.ui.IPageService
	 */
	public void addPerspectiveListener(org.eclipse.ui.IPerspectiveListener l) {
		perspectiveListeners.addPerspectiveListener(l);
	}

	/**
	 * Close the window.
	 * 
	 * Assumes that busy cursor is active.
	 */
	private boolean busyClose(boolean remove) {
		// Whether the window was actually closed or not
		boolean windowClosed = false;

		// Setup internal flags to indicate window is in
		// progress of closing and no update should be done.
		closing = true;
		updateDisabled = true;

		try {
			// Only do the check if it is OK to close if we are not closing
			// via the workbench as the workbench will check this itself.
			Workbench workbench = getWorkbenchImpl();
			int count = workbench.getWorkbenchWindowCount();
			// also check for starting - if the first window dies on startup
			// then we'll need to open a default window.
			if (!workbench.isStarting() && !workbench.isClosing() && count <= 1
					&& workbench.getWorkbenchConfigurer().getExitOnLastWindowClose()) {
				windowClosed = workbench.close();
			} else {
				if (okToClose()) {
					windowClosed = hardClose(remove);
				}
			}
		} finally {
			if (!windowClosed) {
				// Reset the internal flags if window was not closed.
				closing = false;
				updateDisabled = false;
			}
		}

		if (windowClosed && tracker != null) {
			tracker.close();
		}

		return windowClosed;
	}

	public Shell getShell() {
		return (Shell) model.getWidget();
	}

	public boolean close(final boolean remove) {
		final boolean[] ret = new boolean[1];
		BusyIndicator.showWhile(null, new Runnable() {
			public void run() {
				ret[0] = busyClose(remove);
			}
		});
		return ret[0];
	}

	void setRestarting(boolean restarting) {
		this.restarting = restarting;
	}

	/**
	 * @see IWorkbenchWindow
	 */
	public boolean close() {
		return close(true);
	}

	protected boolean isClosing() {
		return closing || getWorkbenchImpl().isClosing();
	}

	/**
	 * Notifies interested parties (namely the advisor) that the window is about
	 * to be opened.
	 * 
	 * @since 3.1
	 */
	private void fireWindowOpening() {
		// let the application do further configuration
		getWindowAdvisor().preWindowOpen();
	}

	/**
	 * Notifies interested parties (namely the advisor) that the window has been
	 * restored from a previously saved state.
	 * 
	 * @throws WorkbenchException
	 *             passed through from the advisor
	 * @since 3.1
	 */
	void fireWindowRestored() throws WorkbenchException {
		StartupThreading.runWithWorkbenchExceptions(new StartupRunnable() {
			public void runWithException() throws Throwable {
				getWindowAdvisor().postWindowRestore();
			}
		});
	}

	/**
	 * Notifies interested parties (namely the advisor and the window listeners)
	 * that the window has been closed.
	 * 
	 * @since 3.1
	 */
	private void fireWindowClosed() {
		// let the application do further deconfiguration
		getWindowAdvisor().postWindowClose();
		getWorkbenchImpl().fireWindowClosed(this);
	}

	/**
	 * Fires perspective activated
	 */
	void firePerspectiveActivated(IWorkbenchPage page, IPerspectiveDescriptor perspective) {
		UIListenerLogging.logPerspectiveEvent(this, page, perspective,
				UIListenerLogging.PLE_PERSP_ACTIVATED);
		perspectiveListeners.firePerspectiveActivated(page, perspective);
	}

	/**
	 * Fires perspective deactivated.
	 * 
	 * @since 3.2
	 */
	void firePerspectivePreDeactivate(IWorkbenchPage page, IPerspectiveDescriptor perspective) {
		UIListenerLogging.logPerspectiveEvent(this, page, perspective,
				UIListenerLogging.PLE_PERSP_PRE_DEACTIVATE);
		perspectiveListeners.firePerspectivePreDeactivate(page, perspective);
	}

	/**
	 * Fires perspective deactivated.
	 * 
	 * @since 3.1
	 */
	void firePerspectiveDeactivated(IWorkbenchPage page, IPerspectiveDescriptor perspective) {
		UIListenerLogging.logPerspectiveEvent(this, page, perspective,
				UIListenerLogging.PLE_PERSP_DEACTIVATED);
		perspectiveListeners.firePerspectiveDeactivated(page, perspective);
	}

	/**
	 * Fires perspective changed
	 */
	public void firePerspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective,
			String changeId) {
		// Some callers call this even when there is no active perspective.
		// Just ignore this case.
		if (perspective != null) {
			UIListenerLogging.logPerspectiveChangedEvent(this, page, perspective, null, changeId);
			perspectiveListeners.firePerspectiveChanged(page, perspective, changeId);
		}
	}

	/**
	 * Fires perspective changed for an affected part
	 */
	public void firePerspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective,
			IWorkbenchPartReference partRef, String changeId) {
		// Some callers call this even when there is no active perspective.
		// Just ignore this case.
		if (perspective != null) {
			UIListenerLogging
					.logPerspectiveChangedEvent(this, page, perspective, partRef, changeId);
			perspectiveListeners.firePerspectiveChanged(page, perspective, partRef, changeId);
		}
	}

	/**
	 * Fires perspective closed
	 */
	void firePerspectiveClosed(IWorkbenchPage page, IPerspectiveDescriptor perspective) {
		UIListenerLogging.logPerspectiveEvent(this, page, perspective,
				UIListenerLogging.PLE_PERSP_CLOSED);
		perspectiveListeners.firePerspectiveClosed(page, perspective);
	}

	/**
	 * Fires perspective opened
	 */
	void firePerspectiveOpened(IWorkbenchPage page, IPerspectiveDescriptor perspective) {
		UIListenerLogging.logPerspectiveEvent(this, page, perspective,
				UIListenerLogging.PLE_PERSP_OPENED);
		perspectiveListeners.firePerspectiveOpened(page, perspective);
	}

	/**
	 * Fires perspective saved as.
	 * 
	 * @since 3.1
	 */
	void firePerspectiveSavedAs(IWorkbenchPage page, IPerspectiveDescriptor oldPerspective,
			IPerspectiveDescriptor newPerspective) {
		UIListenerLogging.logPerspectiveSavedAs(this, page, oldPerspective, newPerspective);
		perspectiveListeners.firePerspectiveSavedAs(page, oldPerspective, newPerspective);
	}

	/**
	 * Returns the action bars for this window.
	 */
	public WWinActionBars getActionBars() {
		if (actionBars == null) {
			actionBars = new WWinActionBars(this);
		}
		return actionBars;
	}

	/**
	 * Returns the active page.
	 * 
	 * @return the active page
	 */
	public IWorkbenchPage getActivePage() {
		return page;
	}

	/**
	 * Returns the number. This corresponds to a page number in a window or a
	 * window number in the workbench.
	 */
	public int getNumber() {
		return number;
	}

	/**
	 * Returns an array of the pages in the workbench window.
	 * 
	 * @return an array of pages
	 */
	public IWorkbenchPage[] getPages() {
		return new IWorkbenchPage[] { page };
	}

	/**
	 * @see IWorkbenchWindow
	 */
	public IPartService getPartService() {
		return page;
	}

	/**
	 * Returns the layout for the shell.
	 * 
	 * @return the layout for the shell
	 */
	protected Layout getLayout() {
		return null;
	}

	/**
	 * @see IWorkbenchWindow
	 */
	public ISelectionService getSelectionService() {
		return selectionService;
	}

	/**
	 * Returns <code>true</code> when the window's shell is activated,
	 * <code>false</code> when it's shell is deactivated
	 * 
	 * @return boolean <code>true</code> when shell activated,
	 *         <code>false</code> when shell deactivated
	 */
	public boolean getShellActivated() {
		return shellActivated;
	}

	/**
	 * @see IWorkbenchWindow
	 */
	public IWorkbench getWorkbench() {
		return PlatformUI.getWorkbench();
	}

	/**
	 * Unconditionally close this window. Assumes the proper flags have been set
	 * correctly (e.i. closing and updateDisabled)
	 * 
	 * @param remove <code>true</code> if this window should be removed from the application model
	 */
	private boolean hardClose(boolean remove) {
		try {
			// clear some lables
			// Remove the handler submissions. Bug 64024.
			final IWorkbench workbench = getWorkbench();
			final IHandlerService handlerService = (IHandlerService) workbench
					.getService(IHandlerService.class);
			handlerService.deactivateHandlers(handlerActivations);
			final Iterator activationItr = handlerActivations.iterator();
			while (activationItr.hasNext()) {
				final IHandlerActivation activation = (IHandlerActivation) activationItr.next();
				activation.getHandler().dispose();
			}
			handlerActivations.clear();
			globalActionHandlersByCommandId.clear();

			// Remove the enabled submissions. Bug 64024.
			final IContextService contextService = (IContextService) workbench
					.getService(IContextService.class);
			contextService.unregisterShell(getShell());

			fireWindowClosed();

			// time to wipe our our populate
			IMenuService menuService = (IMenuService) workbench.getService(IMenuService.class);
			menuService.releaseContributions(((ContributionManager) getActionBars()
					.getMenuManager()));
			ICoolBarManager coolbar = getActionBars().getCoolBarManager();
			if (coolbar != null) {
				menuService.releaseContributions(((ContributionManager) coolbar));
			}

			getActionBarAdvisor().dispose();
			getWindowAdvisor().dispose();

			// Null out the progress region. Bug 64024.
			progressRegion = null;

			model.getContext().set(IContextConstants.ACTIVE_CHILD, null);
			for (MWindowElement windowElement : model.getChildren()) {
				engine.removeGui(windowElement);
			}
			MWindow window = model;
			engine.removeGui(model);

			MElementContainer<MUIElement> parent = window.getParent();
			if (parent.getSelectedElement() == window) {
				if (!parent.getChildren().isEmpty()) {
					parent.setSelectedElement(parent.getChildren().get(0));
				}
			}

			if (remove) {
				window.getParent().getChildren().remove(window);
			}
		} finally {

			try {
				// Bring down all of the services ... after the window goes away
				serviceLocator.dispose();
			} catch (Exception ex) {
				WorkbenchPlugin.log(ex);
			}
			menuRestrictions.clear();
		}
		return true;
	}

	/**
	 * @see IWorkbenchWindow
	 */
	public boolean isApplicationMenu(String menuID) {
		// delegate this question to the action bar advisor
		return getActionBarAdvisor().isApplicationMenu(menuID);
	}

	/**
	 * Called when this window is about to be closed.
	 * 
	 * Subclasses may overide to add code that returns <code>false</code> to
	 * prevent closing under certain conditions.
	 */
	public boolean okToClose() {
		// Save all of the editors.
		if (!getWorkbenchImpl().isClosing()) {
			// if (!saveAllPages(true)) {
			// return false;
			// }
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbenchWindow#openPage(java.lang.String,
	 * org.eclipse.core.runtime.IAdaptable)
	 */
	public IWorkbenchPage openPage(String perspectiveId, IAdaptable input)
			throws WorkbenchException {
		return workbench.openWorkbenchWindow(perspectiveId, input).getActivePage();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.IWorkbenchWindow#openPage(org.eclipse.core.runtime.IAdaptable
	 * )
	 */
	public IWorkbenchPage openPage(IAdaptable input) throws WorkbenchException {
		return openPage(workbench.getPerspectiveRegistry().getDefaultPerspective(), input);
	}

	/*
	 * Removes an listener from the part service.
	 */
	public void removePageListener(IPageListener l) {
		pageListeners.removePageListener(l);
	}

	/**
	 * @see org.eclipse.ui.IPageService
	 */
	public void removePerspectiveListener(org.eclipse.ui.IPerspectiveListener l) {
		perspectiveListeners.removePerspectiveListener(l);
	}

	/*
	 * (non-Javadoc) Method declared on IRunnableContext.
	 */
	public void run(final boolean fork, boolean cancelable, final IRunnableWithProgress runnable)
			throws InvocationTargetException, InterruptedException {
		final StatusLineManager manager = getStatusLineManager();
		if (manager == null) {
			runnable.run(new NullProgressMonitor());
		} else {
			boolean wasCancelEnabled = manager.isCancelEnabled();
			try {
				manager.setCancelEnabled(cancelable);

				final InvocationTargetException[] ite = new InvocationTargetException[1];
				final InterruptedException[] ie = new InterruptedException[1];

				BusyIndicator.showWhile(getShell().getDisplay(), new Runnable() {
					public void run() {
						try {
							ModalContext.run(runnable, fork, manager.getProgressMonitor(),
									getShell().getDisplay());
						} catch (InvocationTargetException e) {
							ite[0] = e;
						} catch (InterruptedException e) {
							ie[0] = e;
						}
					}
				});

				if (ite[0] != null) {
					throw ite[0];
				} else if (ie[0] != null) {
					throw ie[0];
				}
			} finally {
				manager.setCancelEnabled(wasCancelEnabled);
			}
		}
	}

	/**
	 * Sets the active page within the window.
	 * 
	 * @param in
	 *            identifies the new active page, or <code>null</code> for no
	 *            active page
	 */
	public void setActivePage(final IWorkbenchPage in) {
		if (getActivePage() == in) {
			return;
		}

		E4Util.unsupported("setActivePage(page) == broken"); //$NON-NLS-1$
		page = (WorkbenchPage) in;
	}

	private Set menuRestrictions = new HashSet();

	private Boolean valueOf(boolean result) {
		return result ? Boolean.TRUE : Boolean.FALSE;
	}

	public Set getMenuRestrictions() {
		return menuRestrictions;
	}

	void liftRestrictions() {
		if (menuRestrictions.isEmpty()) {
			return;
		}
		EvaluationReference[] refs = (EvaluationReference[]) menuRestrictions
				.toArray(new EvaluationReference[menuRestrictions.size()]);
		IEvaluationService es = (IEvaluationService) serviceLocator
				.getService(IEvaluationService.class);
		IEvaluationContext currentState = es.getCurrentState();
		boolean changeDetected = false;
		for (int i = 0; i < refs.length; i++) {
			EvaluationReference reference = refs[i];
			reference.setPostingChanges(true);

			boolean os = reference.evaluate(currentState);
			reference.clearResult();
			boolean ns = reference.evaluate(currentState);
			if (os != ns) {
				changeDetected = true;
				reference.getListener().propertyChange(
						new PropertyChangeEvent(reference, reference.getProperty(), valueOf(os),
								valueOf(ns)));
			}
		}
		if (changeDetected) {
			IMenuService ms = (IMenuService) getWorkbench().getService(IMenuService.class);
			if (ms instanceof WorkbenchMenuService) {
				((WorkbenchMenuService) ms).updateManagers();
			}
		}
	}

	void imposeRestrictions() {
		Iterator i = menuRestrictions.iterator();
		while (i.hasNext()) {
			EvaluationReference ref = (EvaluationReference) i.next();
			ref.setPostingChanges(false);
		}
	}

	/**
	 * Hooks a listener to track the activation and deactivation of the window's
	 * shell. Notifies the active part and editor of the change
	 */
	void trackShellActivation(Shell shell) {
		getShell().addShellListener(new ShellAdapter() {
			public void shellActivated(ShellEvent event) {
				shellActivated = true;
				serviceLocator.activate();
				getWorkbenchImpl().setActivatedWindow(WorkbenchWindow.this);
				WorkbenchPage currentPage = (WorkbenchPage) getActivePage();
				if (currentPage != null) {
					IWorkbenchPart part = currentPage.getActivePart();
					if (part != null) {
						// PartSite site = (PartSite) part.getSite();
						// site.getPane().shellActivated();
					}
					IEditorPart editor = currentPage.getActiveEditor();
					if (editor != null) {
						// PartSite site = (PartSite) editor.getSite();
						// site.getPane().shellActivated();
					}
					getWorkbenchImpl().fireWindowActivated(WorkbenchWindow.this);
				}
				liftRestrictions();
			}

			public void shellDeactivated(ShellEvent event) {
				shellActivated = false;
				imposeRestrictions();
				serviceLocator.deactivate();
				WorkbenchPage currentPage = (WorkbenchPage) getActivePage();
				if (currentPage != null) {
					IWorkbenchPart part = currentPage.getActivePart();
					if (part != null) {
						// PartSite site = (PartSite) part.getSite();
						// site.getPane().shellDeactivated();
					}
					IEditorPart editor = currentPage.getActiveEditor();
					if (editor != null) {
						// PartSite site = (PartSite) editor.getSite();
						// site.getPane().shellDeactivated();
					}
					getWorkbenchImpl().fireWindowDeactivated(WorkbenchWindow.this);
				}
			}
		});
	}

	/**
	 * update the action bars.
	 */
	public void updateActionBars() {
		if (updateDisabled || updatesDeferred()) {
			return;
		}
	}

	/**
	 * Returns true iff we are currently deferring UI processing due to a large
	 * update
	 * 
	 * @return true iff we are deferring UI updates.
	 * @since 3.1
	 */
	private boolean updatesDeferred() {
		return largeUpdates > 0;
	}

	/**
	 * <p>
	 * Indicates the start of a large update within this window. This is used to
	 * disable CPU-intensive, change-sensitive services that were temporarily
	 * disabled in the midst of large changes. This method should always be
	 * called in tandem with <code>largeUpdateEnd</code>, and the event loop
	 * should not be allowed to spin before that method is called.
	 * </p>
	 * <p>
	 * Important: always use with <code>largeUpdateEnd</code>!
	 * </p>
	 * 
	 * @since 3.1
	 */
	public final void largeUpdateStart() {
		largeUpdates++;
	}

	/**
	 * <p>
	 * Indicates the end of a large update within this window. This is used to
	 * re-enable services that were temporarily disabled in the midst of large
	 * changes. This method should always be called in tandem with
	 * <code>largeUpdateStart</code>, and the event loop should not be allowed
	 * to spin before this method is called.
	 * </p>
	 * <p>
	 * Important: always protect this call by using <code>finally</code>!
	 * </p>
	 * 
	 * @since 3.1
	 */
	public final void largeUpdateEnd() {
		if (--largeUpdates == 0) {
			updateActionBars();
		}
	}

	/**
	 * Update the visible action sets. This method is typically called from a
	 * page when the user changes the visible action sets within the
	 * prespective.
	 */
	public void updateActionSets() {
		if (updateDisabled) {
			return;
		}


	}

	private ListenerList actionSetListeners = null;

	private ListenerList backgroundSaveListeners = new ListenerList(ListenerList.IDENTITY);

	private ISelectionService selectionService;


	final void addActionSetsListener(final IActionSetsListener listener) {
		if (actionSetListeners == null) {
			actionSetListeners = new ListenerList();
		}

		actionSetListeners.add(listener);
	}

	final void removeActionSetsListener(final IActionSetsListener listener) {
		if (actionSetListeners != null) {
			actionSetListeners.remove(listener);
			if (actionSetListeners.isEmpty()) {
				actionSetListeners = null;
			}
		}
	}

	/**
	 * Create the progress indicator for the receiver.
	 * 
	 * @param shell
	 *            the parent shell
	 */
	void createProgressIndicator(Shell shell) {
		if (getWindowConfigurer().getShowProgressIndicator()) {
			TrimmedPartLayout layout = (TrimmedPartLayout) shell.getLayout();
			Composite trimComposite = layout.getTrimComposite(shell, SWT.BOTTOM);
			trimComposite.setLayout(new FillLayout());

			progressRegion = new ProgressRegion();
			progressRegion.createContents(trimComposite, this);
		}

	}

	/**
	 * Returns whether the heap status indicator should be shown.
	 * 
	 * @return <code>true</code> to show the heap status indicator,
	 *         <code>false</code> otherwise
	 */
	private boolean getShowHeapStatus() {
		return // Show if the preference is set or debug option is on
		PrefUtil.getAPIPreferenceStore().getBoolean(
				IWorkbenchPreferenceConstants.SHOW_MEMORY_MONITOR)
				|| Boolean
						.valueOf(
								Platform.getDebugOption(PlatformUI.PLUGIN_ID
										+ "/perf/showHeapStatus")).booleanValue(); //$NON-NLS-1$
	}

	void createHeapStatus(Shell shell) {
		if (getShowHeapStatus()) {
			TrimmedPartLayout layout = (TrimmedPartLayout) shell.getLayout();
			Composite trimComposite = layout.getTrimComposite(shell, SWT.BOTTOM);
			trimComposite.setLayout(new FillLayout());

			heapStatus = new HeapStatus(trimComposite, PrefUtil.getInternalPreferenceStore());
		}
	}

	public void showHeapStatus(boolean show) {
		if (show) {
			if (heapStatus == null) {
				createHeapStatus(getShell());

				TrimmedPartLayout layout = (TrimmedPartLayout) getShell().getLayout();
				Composite trimComposite = layout.getTrimComposite(getShell(), SWT.BOTTOM);
				trimComposite.layout();
			}
		} else {
			if (heapStatus != null) {
				heapStatus.dispose();
				heapStatus = null;
			}
		}
	}

	/**
	 * Returns the unique object that applications use to configure this window.
	 * <p>
	 * IMPORTANT This method is declared package-private to prevent regular
	 * plug-ins from downcasting IWorkbenchWindow to WorkbenchWindow and getting
	 * hold of the workbench window configurer that would allow them to tamper
	 * with the workbench window. The workbench window configurer is available
	 * only to the application.
	 * </p>
	 */
	/* package - DO NOT CHANGE */
	WorkbenchWindowConfigurer getWindowConfigurer() {
		if (windowConfigurer == null) {
			// lazy initialize
			windowConfigurer = new WorkbenchWindowConfigurer(this);
		}
		return windowConfigurer;
	}

	/**
	 * Returns the workbench advisor. Assumes the workbench has been created
	 * already.
	 * <p>
	 * IMPORTANT This method is declared private to prevent regular plug-ins
	 * from downcasting IWorkbenchWindow to WorkbenchWindow and getting hold of
	 * the workbench advisor that would allow them to tamper with the workbench.
	 * The workbench advisor is internal to the application.
	 * </p>
	 */
	private/* private - DO NOT CHANGE */
	WorkbenchAdvisor getAdvisor() {
		return getWorkbenchImpl().getAdvisor();
	}

	/**
	 * Returns the window advisor, creating a new one for this window if needed.
	 * <p>
	 * IMPORTANT This method is declared package private to prevent regular
	 * plug-ins from downcasting IWorkbenchWindow to WorkbenchWindow and getting
	 * hold of the window advisor that would allow them to tamper with the
	 * window. The window advisor is internal to the application.
	 * </p>
	 */
	/* package private - DO NOT CHANGE */
	WorkbenchWindowAdvisor getWindowAdvisor() {
		if (windowAdvisor == null) {
			windowAdvisor = getAdvisor().createWorkbenchWindowAdvisor(getWindowConfigurer());
			Assert.isNotNull(windowAdvisor);
		}
		return windowAdvisor;
	}

	/**
	 * Returns the action bar advisor, creating a new one for this window if
	 * needed.
	 * <p>
	 * IMPORTANT This method is declared private to prevent regular plug-ins
	 * from downcasting IWorkbenchWindow to WorkbenchWindow and getting hold of
	 * the action bar advisor that would allow them to tamper with the window's
	 * action bars. The action bar advisor is internal to the application.
	 * </p>
	 */
	private/* private - DO NOT CHANGE */
	ActionBarAdvisor getActionBarAdvisor() {
		if (actionBarAdvisor == null) {
			actionBarAdvisor = getWindowAdvisor().createActionBarAdvisor(
					getWindowConfigurer().getActionBarConfigurer());
			Assert.isNotNull(actionBarAdvisor);
		}
		return actionBarAdvisor;
	}

	/*
	 * Returns the IWorkbench implementation.
	 */
	private Workbench getWorkbenchImpl() {
		return Workbench.getInstance();
	}

	/**
	 * Fills the window's real action bars.
	 * 
	 * @param flags
	 *            indicate which bars to fill
	 */
	public void fillActionBars(int flags) {
		Workbench workbench = getWorkbenchImpl();
		workbench.largeUpdateStart();
		try {
			getActionBarAdvisor().fillActionBars(flags);
			//
			// 3.3 start
			final IMenuService menuService = (IMenuService) serviceLocator
					.getService(IMenuService.class);
			menuService.populateContributionManager((ContributionManager) getActionBars()
					.getMenuManager(), MenuUtil.MAIN_MENU);
			ICoolBarManager coolbar = getActionBars().getCoolBarManager();
			if (coolbar != null) {
				menuService.populateContributionManager((ContributionManager) coolbar,
						MenuUtil.MAIN_TOOLBAR);
			}
			// 3.3 end
		} finally {
			workbench.largeUpdateEnd();
		}
	}

	/**
	 * Fills the window's proxy action bars.
	 * 
	 * @param proxyBars
	 *            the proxy configurer
	 * @param flags
	 *            indicate which bars to fill
	 */
	public void fillActionBars(IActionBarConfigurer2 proxyBars, int flags) {
		Assert.isNotNull(proxyBars);
		WorkbenchWindowConfigurer.WindowActionBarConfigurer wab = (WorkbenchWindowConfigurer.WindowActionBarConfigurer) getWindowConfigurer()
				.getActionBarConfigurer();
		wab.setProxy(proxyBars);
		try {
			getActionBarAdvisor().fillActionBars(flags | ActionBarAdvisor.FILL_PROXY);
		} finally {
			wab.setProxy(null);
		}
	}



	/**
	 * @param visible
	 *            whether the cool bar should be shown. This is only applicable
	 *            if the window configurer also wishes either the cool bar to be
	 *            visible.
	 * @since 3.0
	 */
	public void setCoolBarVisible(boolean visible) {
		boolean oldValue = coolBarVisible;
		coolBarVisible = visible;
		if (oldValue != coolBarVisible) {

			firePropertyChanged(PROP_COOLBAR_VISIBLE, oldValue ? Boolean.TRUE : Boolean.FALSE,
					coolBarVisible ? Boolean.TRUE : Boolean.FALSE);
		}
	}

	/**
	 * @return whether the cool bar should be shown. This is only applicable if
	 *         the window configurer also wishes either the cool bar to be
	 *         visible.
	 * @since 3.0
	 */
	public boolean getCoolBarVisible() {
		return getWindowConfigurer().getShowCoolBar() && coolBarVisible;
	}

	/**
	 * @param visible
	 *            whether the perspective bar should be shown. This is only
	 *            applicable if the window configurer also wishes either the
	 *            perspective bar to be visible.
	 * @since 3.0
	 */
	public void setPerspectiveBarVisible(boolean visible) {
		boolean oldValue = perspectiveBarVisible;
		perspectiveBarVisible = visible;
		if (oldValue != perspectiveBarVisible) {

			firePropertyChanged(PROP_PERSPECTIVEBAR_VISIBLE, oldValue ? Boolean.TRUE
					: Boolean.FALSE, perspectiveBarVisible ? Boolean.TRUE : Boolean.FALSE);
		}
	}

	/**
	 * @return whether the perspective bar should be shown. This is only
	 *         applicable if the window configurer also wishes either the
	 *         perspective bar to be visible.
	 * @since 3.0
	 */
	public boolean getPerspectiveBarVisible() {
		return getWindowConfigurer().getShowPerspectiveBar() && perspectiveBarVisible;
	}

	/**
	 * Tell the workbench window a visible state for the fastview bar. This is
	 * only applicable if the window configurer also wishes the fast view bar to
	 * be visible.
	 * 
	 * @param visible
	 *            <code>true</code> or <code>false</code>
	 * @since 3.2
	 */
	public void setFastViewBarVisible(boolean visible) {
		boolean oldValue = fastViewBarVisible;
		fastViewBarVisible = visible;
		if (oldValue != fastViewBarVisible) {

		}
	}

	/**
	 * The workbench window take on the fastview bar. This is only applicable if
	 * the window configurer also wishes the fast view bar to be visible.
	 * 
	 * @return <code>true</code> if the workbench window thinks the fastview bar
	 *         should be visible.
	 * @since 3.2
	 */
	public boolean getFastViewBarVisible() {
		return fastViewBarVisible;
	}

	/**
	 * @param visible
	 *            whether the perspective bar should be shown. This is only
	 *            applicable if the window configurer also wishes either the
	 *            perspective bar to be visible.
	 * @since 3.0
	 */
	public void setStatusLineVisible(boolean visible) {
		boolean oldValue = statusLineVisible;
		statusLineVisible = visible;
		if (oldValue != statusLineVisible) {

			firePropertyChanged(PROP_STATUS_LINE_VISIBLE, oldValue ? Boolean.TRUE : Boolean.FALSE,
					statusLineVisible ? Boolean.TRUE : Boolean.FALSE);
		}
	}

	/**
	 * @return whether the perspective bar should be shown. This is only
	 *         applicable if the window configurer also wishes either the
	 *         perspective bar to be visible.
	 * @since 3.0
	 */
	public boolean getStatusLineVisible() {
		return statusLineVisible;
	}



	public boolean getShowFastViewBars() {
		return getWindowConfigurer().getShowFastViewBars();
	}



	/**
	 * Return the action bar presentation used for creating toolbars. This is
	 * for internal use only, used for consistency with the window.
	 * 
	 * @return the presentation used.
	 */
	public IActionBarPresentationFactory getActionBarPresentationFactory() {
		E4Util.unsupported("getActionBarPresentationFactory: doesn't do anything useful, should cause NPE"); //$NON-NLS-1$
		// allow replacement of the actionbar presentation
		IActionBarPresentationFactory actionBarPresentation = null;
		AbstractPresentationFactory presentationFactory = getWindowConfigurer()
				.getPresentationFactory();
		if (presentationFactory instanceof IActionBarPresentationFactory) {
			actionBarPresentation = ((IActionBarPresentationFactory) presentationFactory);
		}

		return actionBarPresentation;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.window.ApplicationWindow#showTopSeperator()
	 */
	protected boolean showTopSeperator() {
		return false;
	}


	/**
	 * @return Returns the progressRegion.
	 */
	public ProgressRegion getProgressRegion() {
		return progressRegion;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbenchWindow#getExtensionTracker()
	 */
	public IExtensionTracker getExtensionTracker() {
		return (IExtensionTracker) model.getContext().get(IExtensionTracker.class.getName());
	}

	/**
	 * Returns the default page input for workbench pages opened in this window.
	 * 
	 * @return the default page input or <code>null</code> if none
	 * @since 3.1
	 */
	IAdaptable getDefaultPageInput() {
		return getWorkbenchImpl().getDefaultPageInput();
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbenchWindow#getTrimManager()
	 */
	public ITrimManager getTrimManager() {
		return null;
	}

	/**
	 * Initializes all of the default command-based services for the workbench
	 * window.
	 */
	private final void initializeDefaultServices() {
		IEclipseContext windowContext = model.getContext();
		serviceLocator.registerService(IWorkbenchLocationService.class,
				new WorkbenchLocationService(IServiceScopes.WINDOW_SCOPE, getWorkbench(), this,
						null, null, null, 1));
		// added back for legacy reasons
		serviceLocator.registerService(IWorkbenchWindow.class, this);

		final ActionCommandMappingService mappingService = new ActionCommandMappingService();
		serviceLocator.registerService(IActionCommandMappingService.class, mappingService);

		final LegacyActionPersistence actionPersistence = new LegacyActionPersistence(this);
		serviceLocator.registerService(LegacyActionPersistence.class, actionPersistence);
		actionPersistence.read();

		selectionService = (ISelectionService) ContextInjectionFactory.make(SelectionService.class,
				model.getContext());

		LegacyHandlerService hs = new LegacyHandlerService(windowContext);
		windowContext.set(IHandlerService.class.getName(), hs);
		readActionSets();

	}

	public final Object getService(final Class key) {
		return serviceLocator.getService(key);
	}

	public final boolean hasService(final Class key) {
		return serviceLocator.hasService(key);
	}

	/**
	 * Toggle the visibility of the coolbar/perspective bar. This method
	 * respects the window configurer and will only toggle visibility if the
	 * item in question was originally declared visible by the window advisor.
	 * 
	 * @since 3.3
	 */
	public void toggleToolbarVisibility() {
		boolean coolbarVisible = getCoolBarVisible();
		boolean perspectivebarVisible = getPerspectiveBarVisible();
		IPreferenceStore prefs = PrefUtil.getInternalPreferenceStore();

		// only toggle the visibility of the components that
		// were on initially
		if (getWindowConfigurer().getShowCoolBar()) {
			setCoolBarVisible(!coolbarVisible);
			prefs.setValue(IPreferenceConstants.COOLBAR_VISIBLE, !coolbarVisible);
		}
		if (getWindowConfigurer().getShowPerspectiveBar()) {
			setPerspectiveBarVisible(!perspectivebarVisible);
			prefs.setValue(IPreferenceConstants.PERSPECTIVEBAR_VISIBLE, !perspectivebarVisible);
		}
		getShell().layout();
	}

	/* package */void addBackgroundSaveListener(IBackgroundSaveListener listener) {
		backgroundSaveListeners.add(listener);
	}

	/* package */void fireBackgroundSaveStarted() {
		Object[] listeners = backgroundSaveListeners.getListeners();
		for (int i = 0; i < listeners.length; i++) {
			IBackgroundSaveListener listener = (IBackgroundSaveListener) listeners[i];
			listener.handleBackgroundSaveStarted();
		}
	}

	/* package */void removeBackgroundSaveListener(IBackgroundSaveListener listener) {
		backgroundSaveListeners.remove(listener);
	}

	public MWindow getModel() {
		return model;
	}

	StatusLineManager statusLineManager = null;

	public StatusLineManager getStatusLineManager() {
		if (statusLineManager == null) {
			Shell shell = (Shell) model.getWidget();
			if (shell != null) {
				TrimmedPartLayout layout = (TrimmedPartLayout) shell.getLayout();
				Composite trimComposite = layout.getTrimComposite(shell, SWT.BOTTOM);
				trimComposite.setLayout(new FillLayout());

				statusLineManager = new StatusLineManager();
				Control control = statusLineManager.createControl(trimComposite);
				control.setSize(control.computeSize(SWT.DEFAULT, SWT.DEFAULT));
			}
		}
		return statusLineManager;
	}

	CoolBarManager2 cm2 = new CoolBarManager2();

	public ICoolBarManager getCoolBarManager2() {
		return cm2;
	}

	public CoolBarManager getCoolBarManager() {
		return cm2;
	}

	MenuManager menuManager = new MenuManager();

	public MenuManager getMenuManager() {
		return menuManager;
	}

	public IMenuManager getMenuBarManager() {
		return menuManager;
	}

	ToolBarManager2 toolBarManager = new ToolBarManager2();

	public IToolBarManager2 getToolBarManager2() {
		return toolBarManager;
	}

	public IToolBarManager getToolBarManager() {
		return getToolBarManager2();
	}
}
