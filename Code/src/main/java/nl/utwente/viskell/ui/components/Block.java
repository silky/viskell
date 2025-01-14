package nl.utwente.viskell.ui.components;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.layout.StackPane;
import nl.utwente.viskell.haskell.expr.Expression;
import nl.utwente.viskell.haskell.expr.LetExpression;
import nl.utwente.viskell.ui.BlockContainer;
import nl.utwente.viskell.ui.CircleMenu;
import nl.utwente.viskell.ui.ComponentLoader;
import nl.utwente.viskell.ui.ToplevelPane;
import nl.utwente.viskell.ui.DragContext;
import nl.utwente.viskell.ui.TrashContainer;
import nl.utwente.viskell.ui.serialize.Bundleable;

/**
 * Base block shaped UI Component that other visual elements will extend from.
 * Blocks can add input and output values by implementing the InputBlock and
 * OutputBlock interfaces.
 * <p>
 * MouseEvents are used for setting the selection state of a block, single
 * clicks can toggle the state to selected. When a block has already been
 * selected and receives another single left click it will toggle a contextual
 * menu for the block.
 * </p>
 * <p>
 * Each block implementation should also feature it's own FXML implementation.
 * </p>
 */
public abstract class Block extends StackPane implements Bundleable, ComponentLoader {
    /** The pane that is used to hold state and place all components on. */
    private final ToplevelPane toplevel;
    
    /** The context that deals with dragging and touch event for this Block */
    protected DragContext dragContext;
    
    /** Whether the anchor types are fresh*/
    private boolean freshAnchorTypes;
    
    /** Status of change updating process in this block. */
    private boolean updateInProgress;
    
    /** The container to which this Block currently belongs */
    protected BlockContainer container;

    /** Whether this block has a meaningful interpretation the current container context. */
    protected boolean inValidContext;
    
    /**
     * @param pane The pane this block belongs to.
     */
    public Block(ToplevelPane pane) {
        this.toplevel = pane;
        this.freshAnchorTypes = false;
        this.updateInProgress = false;
        this.container = pane;
        this.container.attachBlock(this);
        this.inValidContext = true;
        
        // only the actual shape should be selected for events, not the larger outside bounds
        this.setPickOnBounds(false);
        
        // due to the unstable internals of knot moving, we avoid this centering bind for function blocks 
        if (! (this.belongsOnBottom() || this instanceof FunctionBlock)) {
            // make all non container blocks resize themselves around horizontal midpoint to reduce visual movement 
            this.translateXProperty().bind(this.widthProperty().divide(2).negate());
        }
        
        this.dragContext = new DragContext(this);
        this.dragContext.setSecondaryClickAction((p, byMouse) -> CircleMenu.showFor(this, this.localToParent(p), byMouse));
        this.dragContext.setDragFinishAction(event -> refreshContainer());
        this.dragContext.setContactAction(x -> this.getStyleClass().add("activated"));
        this.dragContext.setReleaseAction(x -> this.getStyleClass().removeAll("activated"));
    }

    /** @return the parent CustomUIPane of this component. */
    public final ToplevelPane getToplevel() {
        return this.toplevel;
    }

    public boolean isActivated() {
        return this.dragContext.isActivated();
    }
    
    /**
     * @return All InputAnchors of the block.
     */
    public abstract List<InputAnchor> getAllInputs();

    /**
     * @return All OutputAnchors of the Block.
     */
    public abstract List<OutputAnchor> getAllOutputs();
    
    public List<ConnectionAnchor> getAllAnchors() {
        List<ConnectionAnchor> result = new ArrayList<>(this.getAllInputs());
        result.addAll(this.getAllOutputs());
        return result;
    }
    
    /** @return true if no connected output anchor exist */
    public boolean isBottomMost() {
        for (OutputAnchor anchor : getAllOutputs()) {
            if (anchor.hasConnection()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Starts a new (2 phase) change propagation process from this block.
     */
    public final void initiateConnectionChanges() {
        this.handleConnectionChanges(false);
        this.handleConnectionChanges(true);
    }
    
    /**
     * Connection change preparation; set fresh types in all anchors. 
     */
    public final void prepareConnectionChanges() {
        if (this.updateInProgress || this.freshAnchorTypes) {
            return; // refresh anchor types in each block only once
        }
        this.freshAnchorTypes = true;
        this.refreshAnchorTypes();

        this.inValidContext = this.checkValidInCurrentContainer();
        if (this.inValidContext) {
            this.getStyleClass().removeAll("invalid");
        } else {
            this.getStyleClass().removeAll("invalid");
            this.getStyleClass().add("invalid");
        }
    }
    
    /**
     * Set fresh types in all anchors of this block for the next typechecking cycle.
     */
    protected abstract void refreshAnchorTypes();
    
    /**
     * Handle the expression and types changes caused by modified connections or values.
     * Propagate the changes through connected blocks, and if final phase trigger a visual update.
     * @param finalPhase whether the change propagation is in the second (final) phase.
     */
    public void handleConnectionChanges(boolean finalPhase) {
        if (this.updateInProgress != finalPhase) {
            return; // avoid doing extra work and infinite recursion
        }

        if (! finalPhase) {
            // in first phase ensure that anchor types are refreshed
            this.prepareConnectionChanges();
        }
        
        this.updateInProgress = !finalPhase;
        this.freshAnchorTypes = false;
        
        // First make sure that all connected inputs will be updated too.        
        for (InputAnchor input : this.getAllInputs()) {
            input.getConnection().ifPresent(c -> c.handleConnectionChangesUpwards(finalPhase));
        }
        
        // propagate changes down from the output anchor to connected inputs
        this.getAllOutputs().stream().forEach(output -> output.getOppositeAnchors().forEach(input -> input.handleConnectionChanges(finalPhase)));
        
        // propagate changes to the outside of a choiceblock
        if (container instanceof Lane) {
            ((Lane)container).handleConnectionChanges(finalPhase);
        }
        
        if (finalPhase) {
            // Now that the expressions and types are fully updated, initiate a visual refresh.
            Platform.runLater(() -> this.invalidateVisualState());
        }
    }
    
    /**
     * @param outsideAnchors the set being accumulated of out-of-reach OutputAnchors that are required for the expression.
     * @return The expression this block represents.
     */
    public abstract Expression getLocalExpr(Set<OutputAnchor> outsideAnchors);
    
    /**
     * This method is only used for the inspector window.
     * @return A complete expression of this block and all its dependencies.
     */
    public final Expression getFullExpr() {
        Set<OutputAnchor> outerAnchors = new HashSet<>();
        Expression localExpr = getLocalExpr(outerAnchors);
        
        LetExpression fullExpr = new LetExpression(localExpr, false);
        extendExprGraph(fullExpr, this.toplevel, outerAnchors);
        
        outerAnchors.forEach(block -> block.extendExprGraph(fullExpr, this.toplevel, new HashSet<>()));
        
        return fullExpr;
    }

    /**
     * Extends the expression graph to include all subexpression required
     * @param exprGraph the let expression representing the current expression graph
     * @param container the container to which this expression graph is constrained
     * @param outsideAnchors a mutable set of required OutputAnchors from a surrounding container
     */
    protected void extendExprGraph(LetExpression exprGraph, BlockContainer container, Set<OutputAnchor> outsideAnchors) {
         for (InputAnchor input : this.getAllInputs()) {
             input.extendExprGraph(exprGraph, container, outsideAnchors);
         }
    }
    
    /** Called when the VisualState changed. */
    public abstract void invalidateVisualState();

    /** @return whether this block is visually shown below common blocks (is constant per instance). */
    public boolean belongsOnBottom() {
        return false;
    }
    
    /** @return class-specific properties of this Block. */
    protected ImmutableMap<String, Object> toBundleFragment() {
        return ImmutableMap.of();
    }
    
    /** @return the container to which this block belongs */
    public BlockContainer getContainer() {
        return container;
    }

    /** @return the list of internal containers wrapped inside this block */
    public List<? extends WrappedContainer> getInternalContainers() {
        return ImmutableList.of();
    }
    
    /** @return an independent copy of this Block, or Optional.empty if the internal state is too complex too copy.  */
    public abstract Optional<Block> getNewCopy();
    
    /** Remove all associations of this block with others in preparation of removal, including all connections */
    public void deleteAllLinks() {
        this.getAllInputs().forEach(InputAnchor::removeConnections);
        this.getAllOutputs().forEach(OutputAnchor::removeConnections);
        this.container.detachBlock(this);
        this.container = TrashContainer.instance;
    }
    
    public void moveIntoContainer(BlockContainer target) {
        BlockContainer source = this.container;
        if (source != target) {
            this.container.detachBlock(this);
            this.container = target;
            target.attachBlock(this);
            
            if (this.getInternalContainers().size() > 0) {
                this.toplevel.moveInFrontOfParentContainers(this);
            }
            
            if (source instanceof WrappedContainer) {
                ((WrappedContainer)source).handleConnectionChanges(false);
                ((WrappedContainer)source).handleConnectionChanges(true);
            }
            
            if (target instanceof WrappedContainer) {
                ((WrappedContainer)target).handleConnectionChanges(false);
                ((WrappedContainer)target).handleConnectionChanges(true);
            }
            
            this.initiateConnectionChanges();
        }
        
    }
    
    /** Scans for and attaches to a new container, if any */
    public void refreshContainer() {
        Bounds myBounds = localToScene(getBoundsInLocal());
        Point2D center = new Point2D((myBounds.getMinX()+myBounds.getMaxX())/2, (myBounds.getMinY()+myBounds.getMaxY())/2);
        List<Point2D> corners = ImmutableList.of(
                new Point2D(myBounds.getMinX(), myBounds.getMinY()),
                new Point2D(myBounds.getMaxX(), myBounds.getMinY()),
                new Point2D(myBounds.getMinX(), myBounds.getMaxY()),
                new Point2D(myBounds.getMaxX(), myBounds.getMaxY()));
        
        // use center point plus one corner to determine wherein this block is, to ease moving a block into a small container
        Predicate<Bounds> within = bounds -> bounds.contains(center) && corners.stream().anyMatch(p -> bounds.contains(p));
        
        // a container may never end up in itself or its children
        List<? extends WrappedContainer> internals = this.getInternalContainers();
        Predicate<BlockContainer> notInSelf = con -> internals.stream().noneMatch(internal -> con.isContainedWithin(internal));
        
        BlockContainer newContainer = toplevel.getAllBlockContainers().
            filter(container -> within.test(container.containmentBoundsInScene()) && notInSelf.test(container)).
                reduce((a, b) -> !a.containmentBoundsInScene().contains(b.containmentBoundsInScene()) ? a : b).
                    orElse(this.toplevel);
        
        Bounds fitBounds = this.getBoundsInParent();
        this.moveIntoContainer(newContainer);
        newContainer.expandToFit(new BoundingBox(fitBounds.getMinX()-10, fitBounds.getMinY()-10, fitBounds.getWidth()+20, fitBounds.getHeight()+20));
    }
    
    /** @return whether this block has a meaningful interpretation the current container. */
    public boolean checkValidInCurrentContainer() {
        return ! (this.container instanceof TrashContainer);
    }
    
    public boolean canAlterAnchors() {
        return false;
    }
    
    public void alterAnchorCount(boolean isRemove) {
        // does not if not supported
    }

    @Override
    public Map<String, Object> toBundle() {
        return ImmutableMap.of(
            "kind", getClass().getSimpleName(),
            "id", hashCode(),
            "x", getLayoutX(),
            "y", getLayoutY(),
            "properties", toBundleFragment()
        );
    }

}
