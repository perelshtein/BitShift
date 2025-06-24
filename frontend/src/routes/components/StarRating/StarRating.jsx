import React, { useState } from 'react';
import starSvg from './star.svg'; // Your transparent SVG
import './StarRating.css';

const StarRating = ({ value = 0, onChange, size = 30, disabled = false }) => {
  const [hoverValue, setHoverValue] = useState(null);

  const handleClick = (rating) => {
    if (!disabled && onChange) {
      onChange(rating);
    }
  };

  const handleMouseEnter = (rating) => {
    if (!disabled) {
      setHoverValue(rating);
    }
  };

  const handleMouseLeave = () => {
    if (!disabled) {
      setHoverValue(null);
    }
  };

  return (
    <div className="star-rating" role="radiogroup" aria-label="Rating">
      {[1, 2, 3, 4, 5].map((rating) => {
        // Highlight if: star is selected (value >= rating) OR
        // star is hovered and to the left of or at hoverValue (hoverValue >= rating)
        const isFilled = hoverValue
          ? hoverValue >= rating
          : value >= rating;

        return (
          <button
            key={rating}
            type="button"
            className={`star ${isFilled ? 'filled' : 'empty'}`}
            onClick={() => handleClick(rating)}
            onMouseEnter={() => handleMouseEnter(rating)}
            onMouseLeave={handleMouseLeave}
            disabled={disabled}
            aria-checked={value === rating}
            aria-label={`Rate ${rating} star${rating > 1 ? 's' : ''}`}
          >
            <img
              src={starSvg}
              alt=""
              width={size}
              height={size}
              className="star-image"
            />
          </button>
        );
      })}
    </div>
  );
};

export default StarRating;