package io.github.fishstiz.minecraftcursor;

import io.github.fishstiz.minecraftcursor.api.CursorHandler;
import io.github.fishstiz.minecraftcursor.api.CursorProvider;
import io.github.fishstiz.minecraftcursor.api.CursorType;
import io.github.fishstiz.minecraftcursor.api.ElementRegistrar;
import io.github.fishstiz.minecraftcursor.platform.Services;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class CursorTypeResolver implements ElementRegistrar {
    static final CursorTypeResolver INSTANCE = new CursorTypeResolver();
    private final List<AbstractMap.SimpleImmutableEntry<Class<? extends GuiEventListener>,
            CursorTypeFunction<? extends GuiEventListener>>>
            registry = new ArrayList<>();
    private final HashMap<String, CursorTypeFunction<? extends GuiEventListener>>
            cachedRegistry = new HashMap<>();
    private String lastFailedElement;

    private CursorTypeResolver() {
    }

    @Override
    public <T extends GuiEventListener> void register(CursorHandler<T> cursorHandler) {
        CursorHandler.TargetElement<T> targetElement = cursorHandler.getTargetElement();

        if (targetElement.elementClass().isPresent()) {
            register(targetElement.elementClass().get(), cursorHandler::getCursorType);
        } else if (targetElement.fullyQualifiedClassName().isPresent()) {
            register(targetElement.fullyQualifiedClassName().get(), cursorHandler::getCursorType);
        } else {
            throw new NoClassDefFoundError("Could not register cursor handler: "
                    + cursorHandler.getClass().getName()
                    + " - Target Element Class and FQCN not present");
        }
    }

    @Override
    public <T extends GuiEventListener> void register(String fullyQualifiedClassName, CursorTypeFunction<T> elementToCursorType) {
        try {
            @SuppressWarnings("unchecked")
            Class<T> elementClass = (Class<T>) Class.forName(Services.PLATFORM.mapClassName("intermediary", fullyQualifiedClassName));
            if (!GuiEventListener.class.isAssignableFrom(elementClass)) {
                throw new ClassCastException(fullyQualifiedClassName + " is not a subclass of Element");
            }
            register(elementClass, elementToCursorType);
        } catch (ClassNotFoundException e) {
            MinecraftCursor.LOGGER.error("Error registering element. Class not found: {}", fullyQualifiedClassName);
        } catch (ClassCastException e) {
            MinecraftCursor.LOGGER.error("Error registering element. Invalid class: {}", e.getMessage());
        }
    }

    @Override
    public <T extends GuiEventListener> void register(Class<T> elementClass, CursorTypeFunction<T> elementToCursorType) {
        registry.add(new AbstractMap.SimpleImmutableEntry<>(elementClass, elementToCursorType));
    }

    @SuppressWarnings("unchecked")
    public <T extends GuiEventListener> CursorType resolveCursorType(T element, double mouseX, double mouseY) {
        String elementName = element.getClass().getName();

        try {
            if (element instanceof CursorProvider cursorProvider) {
                CursorType providedCursorType = cursorProvider.getCursorType(mouseX, mouseY);
                if (providedCursorType != null && providedCursorType != CursorType.DEFAULT) {
                    return providedCursorType;
                }
            }

            CursorTypeFunction<T> cursorTypeFunction = (CursorTypeFunction<T>) cachedRegistry.get(elementName);

            if (cursorTypeFunction == null) {
                cursorTypeFunction = (CursorTypeFunction<T>) computeCursorType(element);
                cachedRegistry.put(elementName, cursorTypeFunction);
            }

            CursorType cursorType = cursorTypeFunction.getCursorType(element, mouseX, mouseY);
            return cursorType != null ? cursorType : CursorType.DEFAULT;
        } catch (LinkageError | Exception e) {
            if (!elementName.equals(lastFailedElement)) {
                lastFailedElement = elementName;
                MinecraftCursor.LOGGER.error(
                        "Could not get cursor type for element: {}",
                        Services.PLATFORM.unmapClassName("named", elementName)
                );
            }
        }
        return CursorType.DEFAULT;
    }

    private CursorTypeFunction<? extends GuiEventListener> computeCursorType(GuiEventListener element) {
        for (int i = registry.size() - 1; i >= 0; i--) {
            if (registry.get(i).getKey().isInstance(element)) {
                return registry.get(i).getValue();
            }
        }
        if (element instanceof ContainerEventHandler) {
            return (CursorTypeFunction<ContainerEventHandler>) this::resolveChildCursorType;
        }
        return ElementRegistrar::elementToDefault;
    }

    private <T extends ContainerEventHandler> CursorType resolveChildCursorType(T parentElement, double mouseX, double mouseY) {
        CursorType cursorType = CursorType.DEFAULT;
        for (GuiEventListener child : parentElement.children()) {
            if (child instanceof ContainerEventHandler childParent) {
                CursorType parentCursorType = resolveChildCursorType(childParent, mouseX, mouseY);
                cursorType = parentCursorType != CursorType.DEFAULT ? parentCursorType : cursorType;
            }
            if (child.isMouseOver(mouseX, mouseY)) {
                CursorType childCursorType = resolveCursorType(child, mouseX, mouseY);
                cursorType = childCursorType != CursorType.DEFAULT ? childCursorType : cursorType;
            }
        }
        return cursorType;
    }
}
