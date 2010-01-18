/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.workbench.ui.renderers.swt;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.e4.ui.model.application.MElementContainer;
import org.eclipse.e4.ui.model.application.MGenericStack;
import org.eclipse.e4.ui.model.application.MPart;
import org.eclipse.e4.ui.model.application.MPartStack;
import org.eclipse.e4.ui.model.application.MPlaceholder;
import org.eclipse.e4.ui.model.application.MUIElement;
import org.eclipse.e4.ui.model.application.MWindow;
import org.eclipse.e4.ui.services.events.IEventBroker;
import org.eclipse.e4.ui.widgets.CTabFolder;
import org.eclipse.e4.ui.workbench.swt.internal.AbstractPartRenderer;
import org.eclipse.e4.workbench.ui.IPresentationEngine;
import org.eclipse.e4.workbench.ui.UIEvents;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

/**
 * This class encapsulates the functionality necessary to manage stacks of parts
 * in a 'lazy loading' manner. For these stacks only the currently 'active'
 * child <b>most</b> be rendered so in this class we over ride that default
 * behavior for processing the stack's contents to prevent all of the contents
 * from being rendered, calling 'childAdded' instead. This not only saves time
 * and SWT resources but is necessary in an IDE world where we must not
 * arbitrarily cause plug-in loading.
 * 
 */
public abstract class LazyStackRenderer extends SWTPartRenderer {
	private EventHandler lazyLoader;

	public LazyStackRenderer() {
		super();
	}

	public void init(IEventBroker eventBroker) {
		lazyLoader = new EventHandler() {
			public void handleEvent(Event event) {
				Object element = event.getProperty(UIEvents.EventTags.ELEMENT);

				if (!(element instanceof MGenericStack<?>))
					return;

				MGenericStack<MUIElement> stack = (MGenericStack<MUIElement>) element;

				MUIElement oldSel = (MUIElement) event
						.getProperty(UIEvents.EventTags.OLD_VALUE);
				selectStackElement(stack, oldSel);
				MUIElement selPart = stack.getActiveChild();
				if (selPart != null && selPart.getWidget() == null) {
					IPresentationEngine renderer = (IPresentationEngine) context
							.get(IPresentationEngine.class.getName());
					renderer.createGui(selPart);
					// activate(selPart);
				}
				// else {
				// // HACK!! Should be in StackRenderer
				// if (stack.getWidget() instanceof CTabFolder) {
				// CTabFolder ctf = (CTabFolder) stack.getWidget();
				// CTabItem[] ctis = ctf.getItems();
				// for (int i = 0; i < ctis.length; i++) {
				// if (ctis[i].getData(OWNING_ME) == selPart) {
				// ctf.setSelection(ctis[i]);
				// break;
				// }
				// }
				// }
				// }
			}
		};

		eventBroker.subscribe(UIEvents.buildTopic(
				UIEvents.ElementContainer.TOPIC,
				UIEvents.ElementContainer.ACTIVECHILD), lazyLoader);
	}

	/**
	 * @param eventBroker
	 */
	public void contextDisposed(IEventBroker eventBroker) {
		eventBroker.unsubscribe(lazyLoader);
	}

	public void postProcess(MUIElement element) {
		if (!(element instanceof MElementContainer<?>))
			return;

		MElementContainer<MUIElement> stack = (MElementContainer<MUIElement>) element;
		MUIElement selPart = stack.getActiveChild();

		// If there's no 'active' part defined then pick the first
		if (selPart == null && stack.getChildren().size() > 0) {
			// NOTE: no need to render first because the listener for
			// the active child changing will do it
			int defaultIndex = 0;
			stack.setActiveChild(stack.getChildren().get(defaultIndex));
		} else if (selPart != null && selPart.getWidget() == null) {
			IPresentationEngine renderer = (IPresentationEngine) context
					.get(IPresentationEngine.class.getName());
			renderer.createGui(selPart);
		}
	}

	@Override
	public void processContents(MElementContainer<MUIElement> me) {
		Widget parentWidget = getParentWidget(me);
		if (parentWidget == null)
			return;

		// Lazy Loading: here we only process the contents through childAdded,
		// we specifically do not render them
		for (MUIElement part : me.getChildren()) {
			if (part.isToBeRendered())
				showChild(me, part);
		}
	}

	/**
	 * This method is necessary to allow the parent container to show affordance
	 * (i.e. tabs) for child elements -without- creating the actual part
	 * 
	 * @param me
	 *            The parent model element
	 * @param part
	 *            The child to show the affordance for
	 */
	protected void showChild(MElementContainer<MUIElement> me, MUIElement part) {
	}

	@Override
	public void hookControllerLogic(final MUIElement me) {
		super.hookControllerLogic(me);

		if (!(me instanceof MPartStack))
			return;

		final MPartStack sm = (MPartStack) me;

		// Detect activation...picks up cases where the user clicks on the
		// (already active) part
		if (sm.getWidget() instanceof Control) {
			Control ctrl = (Control) sm.getWidget();
			ctrl.addListener(SWT.Activate,
					new org.eclipse.swt.widgets.Listener() {
						public void handleEvent(
								org.eclipse.swt.widgets.Event event) {
							CTabFolder ctf = (CTabFolder) event.widget;
							MPartStack stack = (MPartStack) ctf
									.getData(OWNING_ME);
							MPart selPart = stack.getActiveChild();
							if (selPart != null)
								activate(selPart);
						}
					});
		}
	}

	public void selectStackElement(MGenericStack<MUIElement> stack,
			MUIElement oldElement) {
		List<MUIElement> goingHidden = new ArrayList<MUIElement>();
		List<MUIElement> becomingVisible = new ArrayList<MUIElement>();
		if (oldElement != null) {
			assert (stack.getChildren().indexOf(oldElement) >= 0);

			// First, get all the elements under the existing 'selected' element
			hideElementRecursive(oldElement, goingHidden);
		}

		// Now process any newly visible elements
		MUIElement curSel = stack.getActiveChild();
		if (curSel != null) {
			showElementRecursive(curSel, becomingVisible);
		}
	}

	private void hideElementRecursive(MUIElement element,
			List<MUIElement> goingHidden) {
		if (element == null || element.getWidget() == null)
			return;

		// Hide any floating windows
		if (element instanceof MWindow && element.getWidget() != null) {
			element.setVisible(false);
		}

		goingHidden.add(element);

		if (element instanceof MGenericStack<?>) {
			// For stacks only the currently selected elements are being hidden
			MGenericStack<?> container = (MGenericStack<?>) element;
			MUIElement curSel = container.getActiveChild();
			hideElementRecursive(curSel, goingHidden);
		} else if (element instanceof MElementContainer<?>) {
			MElementContainer<?> container = (MElementContainer<?>) element;
			for (MUIElement childElement : container.getChildren()) {
				hideElementRecursive(childElement, goingHidden);
			}
		}
	}

	private void showElementRecursive(MUIElement element,
			List<MUIElement> becomingVisible) {
		if (!element.isToBeRendered())
			return;

		if (element instanceof MPlaceholder) {
			swap((MPlaceholder) element);
			element = ((MPlaceholder) element).getRef();
		}

		// Show any floating windows
		if (element instanceof MWindow && element.getWidget() != null) {
			element.setVisible(true);
		}

		becomingVisible.add(element);

		if (element instanceof MGenericStack<?>) {
			// For stacks only the currently selected elements are being visible
			MGenericStack<?> container = (MGenericStack<?>) element;
			MUIElement curSel = container.getActiveChild();
			if (curSel == null && container.getChildren().size() > 0)
				curSel = container.getChildren().get(0);
			if (curSel != null)
				showElementRecursive(curSel, becomingVisible);
		} else if (element instanceof MElementContainer<?>) {
			MElementContainer<?> container = (MElementContainer<?>) element;
			List<MUIElement> kids = new ArrayList<MUIElement>(container
					.getChildren());
			for (MUIElement childElement : kids) {
				showElementRecursive(childElement, becomingVisible);
			}
		}
	}

	public void swap(MPlaceholder placeholder) {
		MUIElement element = placeholder.getRef();

		MElementContainer<MUIElement> elementParent = element.getParent();
		int elementIndex = elementParent.getChildren().indexOf(element);
		MElementContainer<MUIElement> phParent = placeholder.getParent();
		int phIndex = phParent.getChildren().indexOf(placeholder);

		// Remove the two elements from their respective parents
		elementParent.getChildren().remove(element);
		phParent.getChildren().remove(placeholder);

		// swap over the UIElement info
		boolean onTop = element.isOnTop();
		boolean vis = element.isVisible();
		boolean tbr = element.isToBeRendered();
		String cd = element.getContainerData();

		element.setOnTop(placeholder.isOnTop());
		element.setVisible(placeholder.isVisible());
		element.setToBeRendered(placeholder.isToBeRendered());
		element.setContainerData(placeholder.getContainerData());

		placeholder.setOnTop(onTop);
		placeholder.setVisible(vis);
		placeholder.setToBeRendered(tbr);
		placeholder.setContainerData(cd);

		// Add the elements back into the new parents
		elementParent.getChildren().add(elementIndex, placeholder);
		phParent.getChildren().add(phIndex, element);

		if (elementParent.getActiveChild() == element)
			elementParent.setActiveChild(null);

		if (phParent.getActiveChild() == null)
			phParent.setActiveChild(element);

		// directly manage the widget reparent if the parent exists
		if (element.getWidget() instanceof Control) {
			// Swap the control's layout data
			// HACK!! for now use the placeholder's 'renderer' att to hold the
			// value
			Control c = (Control) element.getWidget();
			Object phLayoutData = placeholder.getRenderer();
			placeholder.setRenderer(c.getLayoutData());
			c.setLayoutData(phLayoutData);

			// If the new parent has already been rendered directly move the
			// control
			if (phParent.getWidget() instanceof Composite) {
				AbstractPartRenderer renderer = (AbstractPartRenderer) phParent
						.getRenderer();
				Composite newParent = (Composite) renderer
						.getUIContainer(element);
				c.setParent(newParent);
				Control[] changed = { c };

				// Fix the Z-order
				MUIElement prevElement = null;
				for (MUIElement kid : phParent.getChildren()) {
					if (kid == element) {
						if (prevElement == null) {
							c.moveAbove(null); // first one, on top
						} else {
							c.moveBelow((Control) prevElement.getWidget());
						}
					} else if (kid.getWidget() != null) {
						prevElement = kid;
					}
				}
				newParent.getShell().layout(changed, SWT.CHANGED | SWT.DEFER);
				if (newParent instanceof CTabFolder) {
					CTabFolder ctf = (CTabFolder) newParent;
					if (ctf.getSelection() == null) {
						ctf.setSelection(0);
					}
				}
			}
		}
	}
}